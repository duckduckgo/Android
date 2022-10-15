(function () {
  'use strict';

  // @ts-nocheck
      (() => {
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

  /* global cloneInto, exportFunction, false */

  function getTopLevelURL () {
      try {
          // FROM: https://stackoverflow.com/a/7739035/73479
          // FIX: Better capturing of top level URL so that trackers in embedded documents are not considered first party
          if (window.location !== window.parent.location) {
              return new URL(window.location.href !== 'about:blank' ? document.referrer : window.parent.location.href)
          } else {
              return new URL(window.location.href)
          }
      } catch (error) {
          return new URL(location.href)
      }
  }

  function isUnprotectedDomain (topLevelUrl, featureList) {
      let unprotectedDomain = false;
      const domainParts = topLevelUrl && topLevelUrl.host ? topLevelUrl.host.split('.') : [];

      // walk up the domain to see if it's unprotected
      while (domainParts.length > 1 && !unprotectedDomain) {
          const partialDomain = domainParts.join('.');

          unprotectedDomain = featureList.filter(domain => domain.domain === partialDomain).length > 0;

          domainParts.shift();
      }

      return unprotectedDomain
  }

  function processConfig (data, userList, preferences, platformSpecificFeatures = []) {
      const topLevelUrl = getTopLevelURL();
      const allowlisted = userList.filter(domain => domain === topLevelUrl.host).length > 0;
      const remoteFeatureNames = Object.keys(data.features);
      const platformSpecificFeaturesNotInRemoteConfig = platformSpecificFeatures.filter((featureName) => !remoteFeatureNames.includes(featureName));
      const enabledFeatures = remoteFeatureNames.filter((featureName) => {
          const feature = data.features[featureName];
          return feature.state === 'enabled' && !isUnprotectedDomain(topLevelUrl, feature.exceptions)
      }).concat(platformSpecificFeaturesNotInRemoteConfig); // only disable platform specific features if it's explicitly disabled in remote config
      const isBroken = isUnprotectedDomain(topLevelUrl, data.unprotectedTemporary);
      preferences.site = {
          domain: topLevelUrl.hostname,
          isBroken,
          allowlisted,
          enabledFeatures
      };
      // TODO
      preferences.cookie = {};

      // Copy feature settings from remote config to preferences object
      preferences.featureSettings = {};
      remoteFeatureNames.forEach((featureName) => {
          if (!enabledFeatures.includes(featureName)) {
              return
          }

          preferences.featureSettings[featureName] = data.features[featureName].settings;
      });

      return preferences
  }

  var contentScopeFeatures = (function (exports) {
  'use strict';

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

  /* global cloneInto, exportFunction, false */

  // Only use globalThis for testing this breaks window.wrappedJSObject code in Firefox
  // eslint-disable-next-line no-global-assign
  let globalObj = typeof window === 'undefined' ? globalThis : window;
  let Error$1 = globalObj.Error;

  function getDataKeySync (sessionKey, domainKey, inputData) {
      // eslint-disable-next-line new-cap
      const hmac = new sjcl.misc.hmac(sjcl.codec.utf8String.toBits(sessionKey + domainKey), sjcl.hash.sha256);
      return sjcl.codec.hex.fromBits(hmac.encrypt(inputData))
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
      if ('ancestorOrigins' in globalThis.location) {
          return globalThis.location.ancestorOrigins.length > 0
      }
      // @ts-ignore types do overlap whilst in DOM context
      return globalThis.top !== globalThis
  }

  /**
   * Best guess effort if the document is third party
   * @returns {boolean} if we infer the document is third party
   */
  function isThirdParty () {
      if (!isBeingFramed()) {
          return false
      }
      return !matchHostname(globalThis.location.hostname, getTabOrigin())
  }

  /**
   * Best guess effort of the tabs origin
   * @returns {string|null} inferred tab origin
   */
  function getTabOrigin () {
      let framingOrigin = null;
      try {
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
      const urls = new Set();
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
      const origins = new Set();
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

  /**
   * For each property defined on the object, update it with the target value.
   */
  function overrideProperty (name, prop) {
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
          } catch (e) {
          }
      }
      return prop.origValue
  }

  function defineProperty (object, propertyName, descriptor) {
      {
          Object.defineProperty(object, propertyName, descriptor);
      }
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

  /**
   * Get the value of a config setting.
   * If the value is not set, return the default value.
   * If the value is not an object, return the value.
   * If the value is an object, check its type property.
   *
   * @param {string} featureName
   * @param {object} args
   * @param {string} prop
   * @param {any} defaultValue - The default value to use if the config setting is not set
   * @returns The value of the config setting or the default value
   */
  function getFeatureAttr (featureName, args, prop, defaultValue) {
      let configSetting = getFeatureSetting(featureName, args, prop);

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

          if (configSetting.type === 'undefined') {
              return undefined
          }

          return configSetting.value
      default:
          return defaultValue
      }
  }

  /**
   * @param {string} featureName
   * @param {object} args
   * @param {string} prop
   * @returns {any}
   */
  function getFeatureSetting (featureName, args, prop) {
      const camelFeatureName = camelcase(featureName);
      return args.featureSettings?.[camelFeatureName]?.[prop]
  }

  /**
   * @param {string} featureName
   * @param {object} args
   * @param {string} prop
   * @returns {boolean}
   */
  function getFeatureSettingEnabled (featureName, args, prop) {
      const result = getFeatureSetting(featureName, args, prop);
      return result === 'enabled'
  }

  function getStack () {
      return new Error$1().stack
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
      constructor (featureName, objectScope, property, proxyObject) {
          this.objectScope = objectScope;
          this.property = property;
          this.featureName = featureName;
          this.camelFeatureName = camelcase(this.featureName);
          const outputHandler = (...args) => {
              const isExempt = shouldExemptMethod(this.camelFeatureName);
              if (debug) {
                  postDebugMessage(this.camelFeatureName, {
                      action: isExempt ? 'ignore' : 'restrict',
                      kind: this.property,
                      documentUrl: document.location.href,
                      stack: getStack(),
                      args: JSON.stringify(args[2])
                  });
              }
              // The normal return value
              if (isExempt) {
                  return DDGReflect.apply(...args)
              }
              return proxyObject.apply(...args)
          };
          {
              this._native = objectScope[property];
              const handler = {};
              handler.apply = outputHandler;
              this.internal = new globalObj.Proxy(objectScope[property], handler);
          }
      }

      // Actually apply the proxy to the native property
      overload () {
          {
              this.objectScope[this.property] = this.internal;
          }
      }
  }

  function postDebugMessage (feature, message) {
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

  const windowsSpecificFeatures = ['windowsPermissionUsage'];

  function isWindowsSpecificFeature (featureName) {
      return windowsSpecificFeatures.includes(featureName)
  }

  function __variableDynamicImportRuntime0__(path) {
     switch (path) {
       case './features/cookie.js': return Promise.resolve().then(function () { return cookie; });
       case './features/fingerprinting-audio.js': return Promise.resolve().then(function () { return fingerprintingAudio; });
       case './features/fingerprinting-battery.js': return Promise.resolve().then(function () { return fingerprintingBattery; });
       case './features/fingerprinting-canvas.js': return Promise.resolve().then(function () { return fingerprintingCanvas; });
       case './features/fingerprinting-hardware.js': return Promise.resolve().then(function () { return fingerprintingHardware; });
       case './features/fingerprinting-screen-size.js': return Promise.resolve().then(function () { return fingerprintingScreenSize; });
       case './features/fingerprinting-temporary-storage.js': return Promise.resolve().then(function () { return fingerprintingTemporaryStorage; });
       case './features/google-rejected.js': return Promise.resolve().then(function () { return googleRejected; });
       case './features/gpc.js': return Promise.resolve().then(function () { return gpc; });
       case './features/navigator-interface.js': return Promise.resolve().then(function () { return navigatorInterface; });
       case './features/referrer.js': return Promise.resolve().then(function () { return referrer; });
       case './features/web-compat.js': return Promise.resolve().then(function () { return webCompat; });
       case './features/windows-permission-usage.js': return Promise.resolve().then(function () { return windowsPermissionUsage; });
       default: return Promise.reject(new Error("Unknown variable dynamic import: " + path));
     }
   }

  function shouldRun () {
      // don't inject into non-HTML documents (such as XML documents)
      // but do inject into XHTML documents
      if (document instanceof HTMLDocument === false && (
          document instanceof XMLDocument === false ||
          document.createElement('div') instanceof HTMLDivElement === false
      )) {
          return false
      }
      return true
  }

  let initArgs = null;
  const updates = [];
  const features = [];

  async function load$1 () {
      if (!shouldRun()) {
          return
      }
      const featureNames = [
          'windowsPermissionUsage',
          'webCompat',
          'fingerprintingAudio',
          'fingerprintingBattery',
          'fingerprintingCanvas',
          'cookie',
          'googleRejected',
          'gpc',
          'fingerprintingHardware',
          'referrer',
          'fingerprintingScreenSize',
          'fingerprintingTemporaryStorage',
          'navigatorInterface'
      ];

      for (const featureName of featureNames) {
          const filename = featureName.replace(/([a-zA-Z])(?=[A-Z0-9])/g, '$1-').toLowerCase();
          const feature = __variableDynamicImportRuntime0__(`./features/${filename}.js`).then(({ init, load, update }) => {
              if (load) {
                  load();
              }
              return { featureName, init, update }
          });
          features.push(feature);
      }
  }

  async function init$d (args) {
      initArgs = args;
      if (!shouldRun()) {
          return
      }
      initStringExemptionLists(args);
      const resolvedFeatures = await Promise.all(features);
      resolvedFeatures.forEach(({ init, featureName }) => {
          if (!isFeatureBroken(args, featureName)) {
              init(args);
          }
      });
      // Fire off updates that came in faster than the init
      while (updates.length) {
          const update = updates.pop();
          await updateFeaturesInner(update);
      }
  }

  async function update$1 (args) {
      if (!shouldRun()) {
          return
      }
      if (initArgs === null) {
          updates.push(args);
          return
      }
      updateFeaturesInner(args);
  }

  async function updateFeaturesInner (args) {
      const resolvedFeatures = await Promise.all(features);
      resolvedFeatures.forEach(({ update, featureName }) => {
          if (!isFeatureBroken(initArgs, featureName) && update) {
              update(args);
          }
      });
  }

  class Cookie {
      constructor (cookieString) {
          this.parts = cookieString.split(';');
          this.parse();
      }

      parse () {
          const EXTRACT_ATTRIBUTES = new Set(['max-age', 'expires', 'domain']);
          this.attrIdx = {};
          this.parts.forEach((part, index) => {
              const kv = part.split('=', 1);
              const attribute = kv[0].trim();
              const value = part.slice(kv[0].length + 1);
              if (index === 0) {
                  this.name = attribute;
                  this.value = value;
              } else if (EXTRACT_ATTRIBUTES.has(attribute.toLowerCase())) {
                  this[attribute.toLowerCase()] = value;
                  this.attrIdx[attribute.toLowerCase()] = index;
              }
          });
      }

      getExpiry () {
          // @ts-ignore
          if (!this.maxAge && !this.expires) {
              return NaN
          }
          const expiry = this.maxAge
              ? parseInt(this.maxAge)
              // @ts-ignore
              : (new Date(this.expires) - new Date()) / 1000;
          return expiry
      }

      get maxAge () {
          return this['max-age']
      }

      set maxAge (value) {
          if (this.attrIdx['max-age'] > 0) {
              this.parts.splice(this.attrIdx['max-age'], 1, `max-age=${value}`);
          } else {
              this.parts.push(`max-age=${value}`);
          }
          this.parse();
      }

      toString () {
          return this.parts.join(';')
      }
  }

  /* eslint-disable quote-props */
  /* eslint-disable quotes */
  /* eslint-disable indent */
  /* eslint-disable eol-last */
  /* eslint-disable no-trailing-spaces */
  /* eslint-disable no-multiple-empty-lines */
      const exceptions = [
    {
      "domain": "nespresso.com",
      "reason": "login issues"
    }
  ];
      const excludedCookieDomains = [
    {
      "domain": "hangouts.google.com",
      "reason": "Site breakage"
    },
    {
      "domain": "docs.google.com",
      "reason": "Site breakage"
    },
    {
      "domain": "accounts.google.com",
      "reason": "SSO which needs cookies for auth"
    },
    {
      "domain": "googleapis.com",
      "reason": "Site breakage"
    },
    {
      "domain": "login.live.com",
      "reason": "SSO which needs cookies for auth"
    },
    {
      "domain": "apis.google.com",
      "reason": "Site breakage"
    },
    {
      "domain": "pay.google.com",
      "reason": "Site breakage"
    },
    {
      "domain": "payments.amazon.com",
      "reason": "Site breakage"
    },
    {
      "domain": "payments.amazon.de",
      "reason": "Site breakage"
    },
    {
      "domain": "atlassian.net",
      "reason": "Site breakage"
    },
    {
      "domain": "atlassian.com",
      "reason": "Site breakage"
    },
    {
      "domain": "paypal.com",
      "reason": "Site breakage"
    },
    {
      "domain": "paypal.com",
      "reason": "site breakage"
    },
    {
      "domain": "salesforce.com",
      "reason": "Site breakage"
    },
    {
      "domain": "salesforceliveagent.com",
      "reason": "Site breakage"
    },
    {
      "domain": "force.com",
      "reason": "Site breakage"
    },
    {
      "domain": "disqus.com",
      "reason": "Site breakage"
    },
    {
      "domain": "spotify.com",
      "reason": "Site breakage"
    },
    {
      "domain": "hangouts.google.com",
      "reason": "site breakage"
    },
    {
      "domain": "docs.google.com",
      "reason": "site breakage"
    },
    {
      "domain": "btsport-utils-prod.akamaized.net",
      "reason": "broken videos"
    }
  ];

  let protectionExempted = true;
  const tabOrigin = getTabOrigin();
  let tabExempted = true;

  if (tabOrigin != null) {
      tabExempted = exceptions.some((exception) => {
          return matchHostname(tabOrigin, exception.domain)
      });
  }
  const frameExempted = excludedCookieDomains.some((exception) => {
      return matchHostname(globalThis.location.hostname, exception.domain)
  });
  protectionExempted = frameExempted || tabExempted;

  // Initial cookie policy pre init
  let cookiePolicy = {
      debug: false,
      isFrame: isBeingFramed(),
      isTracker: false,
      shouldBlock: !protectionExempted,
      shouldBlockTrackerCookie: true,
      shouldBlockNonTrackerCookie: true,
      isThirdParty: isThirdParty(),
      policy: {
          threshold: 604800, // 7 days
          maxAge: 604800 // 7 days
      }
  };

  let loadedPolicyResolve;
  // Listen for a message from the content script which will configure the policy for this context
  const trackerHosts = new Set();

  /**
   * @param {'ignore' | 'block' | 'restrict'} action
   * @param {string} reason
   * @param {any} ctx
   */
  function debugHelper (action, reason, ctx) {
      cookiePolicy.debug && postDebugMessage('jscookie', {
          action,
          reason,
          stack: ctx.stack,
          documentUrl: globalThis.document.location.href,
          scriptOrigins: [...ctx.scriptOrigins],
          value: ctx.value
      });
  }

  function shouldBlockTrackingCookie () {
      return cookiePolicy.shouldBlock && cookiePolicy.shouldBlockTrackerCookie && isTrackingCookie()
  }

  function shouldBlockNonTrackingCookie () {
      return cookiePolicy.shouldBlock && cookiePolicy.shouldBlockNonTrackerCookie && isNonTrackingCookie()
  }

  function isTrackingCookie () {
      return cookiePolicy.isFrame && cookiePolicy.isTracker && cookiePolicy.isThirdParty
  }

  function isNonTrackingCookie () {
      return cookiePolicy.isFrame && !cookiePolicy.isTracker && cookiePolicy.isThirdParty
  }

  function load (args) {
      trackerHosts.clear();

      // The cookie policy is injected into every frame immediately so that no cookie will
      // be missed.
      const document = globalThis.document;
      const cookieSetter = Object.getOwnPropertyDescriptor(globalThis.Document.prototype, 'cookie').set;
      const cookieGetter = Object.getOwnPropertyDescriptor(globalThis.Document.prototype, 'cookie').get;

      const loadPolicy = new Promise((resolve) => {
          loadedPolicyResolve = resolve;
      });
      // Create the then callback now - this ensures that Promise.prototype.then changes won't break
      // this call.
      const loadPolicyThen = loadPolicy.then.bind(loadPolicy);

      function getCookiePolicy () {
          const stack = getStack();
          const scriptOrigins = getStackTraceOrigins(stack);
          const getCookieContext = {
              stack,
              scriptOrigins,
              value: 'getter'
          };

          if (shouldBlockTrackingCookie() || shouldBlockNonTrackingCookie()) {
              debugHelper('block', '3p frame', getCookieContext);
              return ''
          } else if (isTrackingCookie() || isNonTrackingCookie()) {
              debugHelper('ignore', '3p frame', getCookieContext);
          }
          return cookieGetter.call(document)
      }

      function setCookiePolicy (value) {
          const stack = getStack();
          const scriptOrigins = getStackTraceOrigins(stack);
          const setCookieContext = {
              stack,
              scriptOrigins,
              value
          };

          if (shouldBlockTrackingCookie() || shouldBlockNonTrackingCookie()) {
              debugHelper('block', '3p frame', setCookieContext);
              return
          } else if (isTrackingCookie() || isNonTrackingCookie()) {
              debugHelper('ignore', '3p frame', setCookieContext);
          }
          // call the native document.cookie implementation. This will set the cookie immediately
          // if the value is valid. We will override this set later if the policy dictates that
          // the expiry should be changed.
          cookieSetter.call(document, value);

          try {
              // wait for config before doing same-site tests
              loadPolicyThen(() => {
                  const { shouldBlock, policy } = cookiePolicy;

                  if (!shouldBlock) {
                      debugHelper('ignore', 'disabled', setCookieContext);
                      return
                  }

                  // extract cookie expiry from cookie string
                  const cookie = new Cookie(value);
                  // apply cookie policy
                  if (cookie.getExpiry() > policy.threshold) {
                      // check if the cookie still exists
                      if (document.cookie.split(';').findIndex(kv => kv.trim().startsWith(cookie.parts[0].trim())) !== -1) {
                          cookie.maxAge = policy.maxAge;

                          debugHelper('restrict', 'expiry', setCookieContext);

                          cookieSetter.apply(document, [cookie.toString()]);
                      } else {
                          debugHelper('ignore', 'dissappeared', setCookieContext);
                      }
                  } else {
                      debugHelper('ignore', 'expiry', setCookieContext);
                  }
              });
          } catch (e) {
              debugHelper('ignore', 'error', setCookieContext);
              // suppress error in cookie override to avoid breakage
              console.warn('Error in cookie override', e);
          }
      }

      defineProperty(document, 'cookie', {
          configurable: true,
          set: setCookiePolicy,
          get: getCookiePolicy
      });
  }

  function init$c (args) {
      args.cookie.debug = args.debug;
      cookiePolicy = args.cookie;

      const featureName = 'cookie';
      cookiePolicy.shouldBlockTrackerCookie = getFeatureSettingEnabled(featureName, args, 'trackerCookie');
      cookiePolicy.shouldBlockNonTrackerCookie = getFeatureSettingEnabled(featureName, args, 'nonTrackerCookie');
      const policy = getFeatureSetting(featureName, args, 'firstPartyCookiePolicy');
      if (policy) {
          cookiePolicy.policy = policy;
      }

      loadedPolicyResolve();
  }

  function update (args) {
      if (args.trackerDefinition) {
          trackerHosts.add(args.hostname);
      }
  }

  var cookie = /*#__PURE__*/Object.freeze({
    __proto__: null,
    load: load,
    init: init$c,
    update: update
  });

  function init$b (args) {
      const { sessionKey, site } = args;
      const domainKey = site.domain;
      const featureName = 'fingerprinting-audio';

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

      const copyFromChannelProxy = new DDGProxy(featureName, AudioBuffer.prototype, 'copyFromChannel', {
          apply (target, thisArg, args) {
              const [source, channelNumber, startInChannel] = args;
              // This is implemented in a different way to canvas purely because calling the function copied the original value, which is not ideal
              if (// If channelNumber is longer than arrayBuffer number of channels then call the default method to throw
                  channelNumber > thisArg.numberOfChannels ||
                  // If startInChannel is longer than the arrayBuffer length then call the default method to throw
                  startInChannel > thisArg.length) {
                  // The normal return value
                  return DDGReflect.apply(target, thisArg, args)
              }
              try {
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

      const getChannelDataProxy = new DDGProxy(featureName, AudioBuffer.prototype, 'getChannelData', {
          apply (target, thisArg, args) {
              // The normal return value
              const channelData = DDGReflect.apply(target, thisArg, args);
              // Anything we do here should be caught and ignored silently
              try {
                  transformArrayData(channelData, domainKey, sessionKey, thisArg, args);
              } catch {
              }
              return channelData
          }
      });
      getChannelDataProxy.overload();

      const audioMethods = ['getByteTimeDomainData', 'getFloatTimeDomainData', 'getByteFrequencyData', 'getFloatFrequencyData'];
      for (const methodName of audioMethods) {
          const proxy = new DDGProxy(featureName, AnalyserNode.prototype, methodName, {
              apply (target, thisArg, args) {
                  DDGReflect.apply(target, thisArg, args);
                  // Anything we do here should be caught and ignored silently
                  try {
                      transformArrayData(args[0], domainKey, sessionKey, thisArg, args);
                  } catch {
                  }
              }
          });
          proxy.overload();
      }
  }

  var fingerprintingAudio = /*#__PURE__*/Object.freeze({
    __proto__: null,
    init: init$b
  });

  /**
   * Overwrites the Battery API if present in the browser.
   * It will return the values defined in the getBattery function to the client,
   * as well as prevent any script from listening to events.
   */
  function init$a (args) {
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
                  defineProperty(BatteryManager.prototype, prop, { get: () => val });
              } catch (e) { }
          }
          for (const eventProp of eventProperties) {
              try {
                  defineProperty(BatteryManager.prototype, eventProp, { get: () => null });
              } catch (e) { }
          }
      }
  }

  var fingerprintingBattery = /*#__PURE__*/Object.freeze({
    __proto__: null,
    init: init$a
  });

  var commonjsGlobal = typeof globalThis !== 'undefined' ? globalThis : typeof window !== 'undefined' ? window : typeof global !== 'undefined' ? global : typeof self !== 'undefined' ? self : {};

  var alea$1 = {exports: {}};

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

  var xor128$1 = {exports: {}};

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

  var xorwow$1 = {exports: {}};

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

  var xorshift7$1 = {exports: {}};

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

  var xor4096$1 = {exports: {}};

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

  var tychei$1 = {exports: {}};

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
  var alea = alea$1.exports;

  // xor128, a pure xor-shift generator by George Marsaglia.
  // Period: 2^128-1.
  // Reported to fail: MatrixRank and LinearComp.
  var xor128 = xor128$1.exports;

  // xorwow, George Marsaglia's 160-bit xor-shift combined plus weyl.
  // Period: 2^192-2^32
  // Reported to fail: CollisionOver, SimpPoker, and LinearComp.
  var xorwow = xorwow$1.exports;

  // xorshift7, by François Panneton and Pierre L'ecuyer, takes
  // a different approach: it adds robustness by allowing more shifts
  // than Marsaglia's original three.  It is a 7-shift generator
  // with 256 bits, that passes BigCrush with no systmatic failures.
  // Period 2^256-1.
  // No systematic BigCrush failures reported.
  var xorshift7 = xorshift7$1.exports;

  // xor4096, by Richard Brent, is a 4096-bit xor-shift with a
  // very long period that also adds a Weyl generator. It also passes
  // BigCrush with no systematic failures.  Its long period may
  // be useful if you have many generators and need to avoid
  // collisions.
  // Period: 2^4128-2^32.
  // No systematic BigCrush failures reported.
  var xor4096 = xor4096$1.exports;

  // Tyche-i, by Samuel Neves and Filipe Araujo, is a bit-shifting random
  // number generator derived from ChaCha, a modern stream cipher.
  // https://eden.dei.uc.pt/~sneves/pubs/2011-snfa2.pdf
  // Period: ~2^127
  // No systematic BigCrush failures reported.
  var tychei = tychei$1.exports;

  // The original ARC4-based prng included in this library.
  // Period: ~2^1600
  var sr = seedrandom$1.exports;

  sr.alea = alea;
  sr.xor128 = xor128;
  sr.xorwow = xorwow;
  sr.xorshift7 = xorshift7;
  sr.xor4096 = xor4096;
  sr.tychei = tychei;

  var seedrandom = sr;

  /**
   * @param {HTMLCanvasElement} canvas
   * @param {string} domainKey
   * @param {string} sessionKey
   * @param {any} getImageDataProxy
   * @param {CanvasRenderingContext2D | WebGL2RenderingContext | WebGLRenderingContext} ctx?
   */
  function computeOffScreenCanvas (canvas, domainKey, sessionKey, getImageDataProxy, ctx) {
      if (!ctx) {
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
          rasterizedCtx = offScreenCtx;
          offScreenCtx.drawImage(canvas, 0, 0);
      }

      // We *always* compute the random pixels on the complete pixel set, then pass back the subset later
      let imageData = getImageDataProxy._native.apply(rasterizedCtx, [0, 0, canvas.width, canvas.height]);
      imageData = modifyPixelData(imageData, sessionKey, domainKey, canvas.width);

      if (rasterizeToCanvas) {
          clearCanvas(offScreenCtx);
      }

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
      const rng = new seedrandom(windowHash);
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

  function init$9 (args) {
      const { sessionKey, site } = args;
      const domainKey = site.domain;
      const featureName = 'fingerprinting-canvas';
      const supportsWebGl = getFeatureSettingEnabled(featureName, args, 'webGl');

      const unsafeCanvases = new WeakSet();
      const canvasContexts = new WeakMap();
      const canvasCache = new WeakMap();

      /**
       * Clear cache as canvas has changed
       * @param {HTMLCanvasElement} canvas
       */
      function clearCache (canvas) {
          canvasCache.delete(canvas);
      }

      /**
       * @param {HTMLCanvasElement} canvas
       */
      function treatAsUnsafe (canvas) {
          unsafeCanvases.add(canvas);
          clearCache(canvas);
      }

      const proxy = new DDGProxy(featureName, HTMLCanvasElement.prototype, 'getContext', {
          apply (target, thisArg, args) {
              const context = DDGReflect.apply(target, thisArg, args);
              try {
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
          const safeMethodProxy = new DDGProxy(featureName, CanvasRenderingContext2D.prototype, methodName, {
              apply (target, thisArg, args) {
                  // Don't apply escape hatch for canvases
                  if (methodName === 'drawImage' && args[0] && args[0] instanceof HTMLCanvasElement) {
                      treatAsUnsafe(args[0]);
                  } else {
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
              const unsafeProxy = new DDGProxy(featureName, CanvasRenderingContext2D.prototype, methodName, {
                  apply (target, thisArg, args) {
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
              WebGL2RenderingContext,
              WebGLRenderingContext
          ];
          for (const context of glContexts) {
              for (const methodName of unsafeGlMethods) {
                  // Some methods are browser specific
                  if (methodName in context.prototype) {
                      const unsafeProxy = new DDGProxy(featureName, context.prototype, methodName, {
                          apply (target, thisArg, args) {
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
      const getImageDataProxy = new DDGProxy(featureName, CanvasRenderingContext2D.prototype, 'getImageData', {
          apply (target, thisArg, args) {
              if (!unsafeCanvases.has(thisArg.canvas)) {
                  return DDGReflect.apply(target, thisArg, args)
              }
              // Anything we do here should be caught and ignored silently
              try {
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
          const proxy = new DDGProxy(featureName, HTMLCanvasElement.prototype, methodName, {
              apply (target, thisArg, args) {
                  // Short circuit for low risk canvas calls
                  if (!unsafeCanvases.has(thisArg)) {
                      return DDGReflect.apply(target, thisArg, args)
                  }
                  try {
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

  var fingerprintingCanvas = /*#__PURE__*/Object.freeze({
    __proto__: null,
    init: init$9
  });

  const featureName$1 = 'fingerprinting-hardware';

  function init$8 (args) {
      const Navigator = globalThis.Navigator;
      const navigator = globalThis.navigator;

      overrideProperty('keyboard', {
          object: Navigator.prototype,
          origValue: navigator.keyboard,
          targetValue: getFeatureAttr(featureName$1, args, 'keyboard')
      });
      overrideProperty('hardwareConcurrency', {
          object: Navigator.prototype,
          origValue: navigator.hardwareConcurrency,
          targetValue: getFeatureAttr(featureName$1, args, 'hardwareConcurrency', 2)
      });
      overrideProperty('deviceMemory', {
          object: Navigator.prototype,
          origValue: navigator.deviceMemory,
          targetValue: getFeatureAttr(featureName$1, args, 'deviceMemory', 8)
      });
  }

  var fingerprintingHardware = /*#__PURE__*/Object.freeze({
    __proto__: null,
    init: init$8
  });

  const featureName = 'fingerprinting-screen-size';

  /**
   * normalize window dimensions, if more than one monitor is in play.
   *  X/Y values are set in the browser based on distance to the main monitor top or left, which
   * can mean second or more monitors have very large or negative values. This function maps a given
   * given coordinate value to the proper place on the main screen.
   */
  function normalizeWindowDimension (value, targetDimension) {
      if (value > targetDimension) {
          return value % targetDimension
      }
      if (value < 0) {
          return targetDimension + value
      }
      return value
  }

  function setWindowPropertyValue (property, value) {
      // Here we don't update the prototype getter because the values are updated dynamically
      try {
          defineProperty(globalThis, property, {
              get: () => value,
              set: () => {},
              configurable: true
          });
      } catch (e) {}
  }

  const origPropertyValues = {};

  /**
   * Fix window dimensions. The extension runs in a different JS context than the
   * page, so we can inject the correct screen values as the window is resized,
   * ensuring that no information is leaked as the dimensions change, but also that the
   * values change correctly for valid use cases.
   */
  function setWindowDimensions () {
      try {
          const window = globalThis;
          const top = globalThis.top;

          const normalizedY = normalizeWindowDimension(window.screenY, window.screen.height);
          const normalizedX = normalizeWindowDimension(window.screenX, window.screen.width);
          if (normalizedY <= origPropertyValues.availTop) {
              setWindowPropertyValue('screenY', 0);
              setWindowPropertyValue('screenTop', 0);
          } else {
              setWindowPropertyValue('screenY', normalizedY);
              setWindowPropertyValue('screenTop', normalizedY);
          }

          if (top.window.outerHeight >= origPropertyValues.availHeight - 1) {
              setWindowPropertyValue('outerHeight', top.window.screen.height);
          } else {
              try {
                  setWindowPropertyValue('outerHeight', top.window.outerHeight);
              } catch (e) {
                  // top not accessible to certain iFrames, so ignore.
              }
          }

          if (normalizedX <= origPropertyValues.availLeft) {
              setWindowPropertyValue('screenX', 0);
              setWindowPropertyValue('screenLeft', 0);
          } else {
              setWindowPropertyValue('screenX', normalizedX);
              setWindowPropertyValue('screenLeft', normalizedX);
          }

          if (top.window.outerWidth >= origPropertyValues.availWidth - 1) {
              setWindowPropertyValue('outerWidth', top.window.screen.width);
          } else {
              try {
                  setWindowPropertyValue('outerWidth', top.window.outerWidth);
              } catch (e) {
                  // top not accessible to certain iFrames, so ignore.
              }
          }
      } catch (e) {
          // in a cross domain iFrame, top.window is not accessible.
      }
  }

  function init$7 (args) {
      const Screen = globalThis.Screen;
      const screen = globalThis.screen;

      origPropertyValues.availTop = overrideProperty('availTop', {
          object: Screen.prototype,
          origValue: screen.availTop,
          targetValue: getFeatureAttr(featureName, args, 'availTop', 0)
      });
      origPropertyValues.availLeft = overrideProperty('availLeft', {
          object: Screen.prototype,
          origValue: screen.availLeft,
          targetValue: getFeatureAttr(featureName, args, 'availLeft', 0)
      });
      origPropertyValues.availWidth = overrideProperty('availWidth', {
          object: Screen.prototype,
          origValue: screen.availWidth,
          targetValue: screen.width
      });
      origPropertyValues.availHeight = overrideProperty('availHeight', {
          object: Screen.prototype,
          origValue: screen.availHeight,
          targetValue: screen.height
      });
      overrideProperty('colorDepth', {
          object: Screen.prototype,
          origValue: screen.colorDepth,
          targetValue: getFeatureAttr(featureName, args, 'colorDepth', 24)
      });
      overrideProperty('pixelDepth', {
          object: Screen.prototype,
          origValue: screen.pixelDepth,
          targetValue: getFeatureAttr(featureName, args, 'pixelDepth', 24)
      });

      window.addEventListener('resize', function () {
          setWindowDimensions();
      });
      setWindowDimensions();
  }

  var fingerprintingScreenSize = /*#__PURE__*/Object.freeze({
    __proto__: null,
    init: init$7
  });

  function init$6 () {
      const navigator = globalThis.navigator;
      const Navigator = globalThis.Navigator;

      /**
       * Temporary storage can be used to determine hard disk usage and size.
       * This will limit the max storage to 4GB without completely disabling the
       * feature.
       */
      if (navigator.webkitTemporaryStorage) {
          try {
              const org = navigator.webkitTemporaryStorage.queryUsageAndQuota;
              const tStorage = navigator.webkitTemporaryStorage;
              tStorage.queryUsageAndQuota = function queryUsageAndQuota (callback, err) {
                  const modifiedCallback = function (usedBytes, grantedBytes) {
                      const maxBytesGranted = 4 * 1024 * 1024 * 1024;
                      const spoofedGrantedBytes = Math.min(grantedBytes, maxBytesGranted);
                      callback(usedBytes, spoofedGrantedBytes);
                  };
                  org.call(navigator.webkitTemporaryStorage, modifiedCallback, err);
              };
              defineProperty(Navigator.prototype, 'webkitTemporaryStorage', { get: () => tStorage });
          } catch (e) {}
      }
  }

  var fingerprintingTemporaryStorage = /*#__PURE__*/Object.freeze({
    __proto__: null,
    init: init$6
  });

  function init$5 () {
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

  var googleRejected = /*#__PURE__*/Object.freeze({
    __proto__: null,
    init: init$5
  });

  // Set Global Privacy Control property on DOM
  function init$4 (args) {
      try {
          // If GPC on, set DOM property prototype to true if not already true
          if (args.globalPrivacyControlValue) {
              if (navigator.globalPrivacyControl) return
              defineProperty(Navigator.prototype, 'globalPrivacyControl', {
                  get: () => true,
                  configurable: true,
                  enumerable: true
              });
          } else {
              // If GPC off & unsupported by browser, set DOM property prototype to false
              // this may be overwritten by the user agent or other extensions
              if (typeof navigator.globalPrivacyControl !== 'undefined') return
              defineProperty(Navigator.prototype, 'globalPrivacyControl', {
                  get: () => false,
                  configurable: true,
                  enumerable: true
              });
          }
      } catch {
          // Ignore exceptions that could be caused by conflicting with other extensions
      }
  }

  var gpc = /*#__PURE__*/Object.freeze({
    __proto__: null,
    init: init$4
  });

  function init$3 (args) {
      try {
          if (navigator.duckduckgo) {
              return
          }
          if (!args.platform || !args.platform.name) {
              return
          }
          defineProperty(Navigator.prototype, 'duckduckgo', {
              value: {
                  platform: args.platform.name,
                  isDuckDuckGo () {
                      return DDGPromise.resolve(true)
                  }
              },
              enumerable: true,
              configurable: false,
              writable: false
          });
      } catch {
          // todo: Just ignore this exception?
      }
  }

  var navigatorInterface = /*#__PURE__*/Object.freeze({
    __proto__: null,
    init: init$3
  });

  function init$2 (args) {
      // Unfortunately, we only have limited information about the referrer and current frame. A single
      // page may load many requests and sub frames, all with different referrers. Since we
      if (args.referrer && // make sure the referrer was set correctly
          args.referrer.referrer !== undefined && // referrer value will be undefined when it should be unchanged.
          document.referrer && // don't change the value if it isn't set
          document.referrer !== '' && // don't add referrer information
          new URL(document.URL).hostname !== new URL(document.referrer).hostname) { // don't replace the referrer for the current host.
          let trimmedReferer = document.referrer;
          if (new URL(document.referrer).hostname === args.referrer.referrerHost) {
              // make sure the real referrer & replacement referrer match if we're going to replace it
              trimmedReferer = args.referrer.referrer;
          } else {
              // if we don't have a matching referrer, just trim it to origin.
              trimmedReferer = new URL(document.referrer).origin + '/';
          }
          overrideProperty('referrer', {
              object: Document.prototype,
              origValue: document.referrer,
              targetValue: trimmedReferer
          });
      }
  }

  var referrer = /*#__PURE__*/Object.freeze({
    __proto__: null,
    init: init$2
  });

  /**
   * Fixes incorrect sizing value for outerHeight and outerWidth
   */
  function windowSizingFix () {
      window.outerHeight = window.innerHeight;
      window.outerWidth = window.innerWidth;
  }

  /**
   * Add missing navigator.credentials API
   */
  function navigatorCredentialsFix () {
      try {
          const value = {
              get () {
                  return Promise.reject(new Error())
              }
          };
          defineProperty(Navigator.prototype, 'credentials', {
              value,
              configurable: true,
              enumerable: true
          });
      } catch {
          // Ignore exceptions that could be caused by conflicting with other extensions
      }
  }

  function init$1 () {
      windowSizingFix();
      navigatorCredentialsFix();
  }

  var webCompat = /*#__PURE__*/Object.freeze({
    __proto__: null,
    init: init$1
  });

  /* global Bluetooth, Geolocation, HID, Serial, USB */

  function init () {
      const featureName = 'windows-permission-usage';

      const Permission = {
          Geolocation: 'geolocation',
          Camera: 'camera',
          Microphone: 'microphone'
      };

      const Status = {
          Inactive: 'inactive',
          Accessed: 'accessed',
          Active: 'active',
          Paused: 'paused'
      };

      const isFrameInsideFrame = window.self !== window.top && window.parent !== window.top;

      function windowsPostMessage (name, data) {
          window.chrome.webview.postMessage({
              Feature: 'Permissions',
              Name: name,
              Data: data
          });
      }

      function windowsPostGeolocationMessage (name, data) {
          window.chrome.webview.postMessage({
              Feature: 'Geolocation',
              Name: name,
              Data: data
          });
      }

      function signalPermissionStatus (permission, status) {
          windowsPostMessage('PermissionStatusMessage', { permission, status });
          console.debug(`Permission '${permission}' is ${status}`);
      }

      function registerPositionMessageHandler (args, messageId, geolocationActiveStatus) {
          const successHandler = args[0];

          const handler = function ({ data }) {
              if (data?.id === messageId) {
                  window.chrome.webview.removeEventListener('message', handler);
                  signalPermissionStatus(Permission.Geolocation, geolocationActiveStatus);
                  if (Object.prototype.hasOwnProperty.call(data, 'errorCode')) {
                      if (args.length >= 2) {
                          const errorHandler = args[1];
                          const error = { code: data.errorCode, message: data.errorMessage };
                          errorHandler?.(error);
                      }
                  } else {
                      const rez = {
                          timestamp: data.timestamp,
                          coords: {
                              latitude: data.latitude,
                              longitude: data.longitude,
                              altitude: null,
                              altitudeAccuracy: null,
                              heading: null,
                              speed: null,
                              accuracy: data.accuracy
                          }
                      };

                      successHandler?.(rez);
                  }
              }
          };

          window.chrome.webview.addEventListener('message', handler);
      }

      let watchedPositionId = 0;
      const watchedPositions = new Set();
      // proxy for navigator.geolocation.watchPosition -> show red geolocation indicator
      const watchPositionProxy = new DDGProxy(featureName, Geolocation.prototype, 'watchPosition', {
          apply (target, thisArg, args) {
              if (isFrameInsideFrame) {
                  // we can't communicate with iframes inside iframes -> deny permission instead of putting users at risk
                  throw new DOMException('Permission denied')
              }

              const messageId = crypto.randomUUID();
              registerPositionMessageHandler(args, messageId, Status.Active);
              windowsPostGeolocationMessage('positionRequested', { id: messageId });
              watchedPositionId++;
              watchedPositions.add(watchedPositionId);
              return watchedPositionId
          }
      });
      watchPositionProxy.overload();

      // proxy for navigator.geolocation.clearWatch -> clear red geolocation indicator
      const clearWatchProxy = new DDGProxy(featureName, Geolocation.prototype, 'clearWatch', {
          apply (target, thisArg, args) {
              if (args[0] && watchedPositions.delete(args[0]) && watchedPositions.size === 0) {
                  signalPermissionStatus(Permission.Geolocation, Status.Inactive);
              }
          }
      });
      clearWatchProxy.overload();

      // proxy for navigator.geolocation.getCurrentPosition -> normal geolocation indicator
      const getCurrentPositionProxy = new DDGProxy(featureName, Geolocation.prototype, 'getCurrentPosition', {
          apply (target, thisArg, args) {
              const messageId = crypto.randomUUID();
              registerPositionMessageHandler(args, messageId, Status.Accessed);
              windowsPostGeolocationMessage('positionRequested', { id: messageId });
          }
      });
      getCurrentPositionProxy.overload();

      const userMediaStreams = new Set();
      const videoTracks = new Set();
      const audioTracks = new Set();

      function getTracks (permission) {
          switch (permission) {
          case Permission.Camera:
              return videoTracks
          case Permission.Microphone:
              return audioTracks
          }
      }

      function pause (permission) {
          const streamTracks = getTracks(permission);
          streamTracks?.forEach(track => {
              track.enabled = false;
          });
      }

      function resume (permission) {
          const streamTracks = getTracks(permission);
          streamTracks?.forEach(track => {
              track.enabled = true;
          });
      }

      function stop (permission) {
          const streamTracks = getTracks(permission);
          streamTracks?.forEach(track => track.stop());
      }

      function monitorTrack (track) {
          if (track.readyState === 'ended') return

          if (track.kind === 'video' && !videoTracks.has(track)) {
              console.debug(`New video stream track ${track.id}`);
              track.addEventListener('ended', videoTrackEnded);
              track.addEventListener('mute', signalVideoTracksState);
              track.addEventListener('unmute', signalVideoTracksState);
              videoTracks.add(track);
          } else if (track.kind === 'audio' && !audioTracks.has(track)) {
              console.debug(`New audio stream track ${track.id}`);
              track.addEventListener('ended', audioTrackEnded);
              track.addEventListener('mute', signalAudioTracksState);
              track.addEventListener('unmute', signalAudioTracksState);
              audioTracks.add(track);
          }
      }

      function handleTrackEnded (track) {
          if (track.kind === 'video' && videoTracks.has(track)) {
              console.debug(`Video stream track ${track.id} ended`);
              track.removeEventListener('ended', videoTrackEnded);
              track.removeEventListener('mute', signalVideoTracksState);
              track.removeEventListener('unmute', signalVideoTracksState);
              videoTracks.delete(track);
              signalVideoTracksState();
          } else if (track.kind === 'audio' && audioTracks.has(track)) {
              console.debug(`Audio stream track ${track.id} ended`);
              track.removeEventListener('ended', audioTrackEnded);
              track.removeEventListener('mute', signalAudioTracksState);
              track.removeEventListener('unmute', signalAudioTracksState);
              audioTracks.delete(track);
              signalAudioTracksState();
          }
      }

      function videoTrackEnded (e) {
          handleTrackEnded(e.target);
      }

      function audioTrackEnded (e) {
          handleTrackEnded(e.target);
      }

      function signalTracksState (permission) {
          const tracks = getTracks(permission);
          if (!tracks) return

          const allTrackCount = tracks.size;
          if (allTrackCount === 0) {
              signalPermissionStatus(permission, Status.Inactive);
              return
          }

          let mutedTrackCount = 0;
          tracks.forEach(track => {
              mutedTrackCount += ((!track.enabled || track.muted) ? 1 : 0);
          });
          if (mutedTrackCount === allTrackCount) {
              signalPermissionStatus(permission, Status.Paused);
          } else {
              if (mutedTrackCount > 0) {
                  console.debug(`Some ${permission} tracks are still active: ${allTrackCount - mutedTrackCount}/${allTrackCount}`);
              }
              signalPermissionStatus(permission, Status.Active);
          }
      }

      let signalVideoTracksStateTimer;
      function signalVideoTracksState () {
          clearTimeout(signalVideoTracksStateTimer);
          signalVideoTracksStateTimer = setTimeout(() => signalTracksState(Permission.Camera), 100);
      }

      let signalAudioTracksStateTimer;
      function signalAudioTracksState () {
          clearTimeout(signalAudioTracksStateTimer);
          signalAudioTracksStateTimer = setTimeout(() => signalTracksState(Permission.Microphone), 100);
      }

      // proxy for track.stop -> clear camera/mic indicator manually here because no ended event raised this way
      const stopTrackProxy = new DDGProxy(featureName, MediaStreamTrack.prototype, 'stop', {
          apply (target, thisArg, args) {
              handleTrackEnded(thisArg);
              return DDGReflect.apply(target, thisArg, args)
          }
      });
      stopTrackProxy.overload();

      // proxy for track.clone -> monitor the cloned track
      const cloneTrackProxy = new DDGProxy(featureName, MediaStreamTrack.prototype, 'clone', {
          apply (target, thisArg, args) {
              const clonedTrack = DDGReflect.apply(target, thisArg, args);
              if (clonedTrack && (videoTracks.has(thisArg) || audioTracks.has(thisArg))) {
                  console.debug(`Media stream track ${thisArg.id} has been cloned to track ${clonedTrack.id}`);
                  monitorTrack(clonedTrack);
              }
              return clonedTrack
          }
      });
      cloneTrackProxy.overload();

      // override MediaStreamTrack.enabled -> update active/paused status when enabled is set
      const trackEnabledPropertyDescriptor = Object.getOwnPropertyDescriptor(MediaStreamTrack.prototype, 'enabled');
      defineProperty(MediaStreamTrack.prototype, 'enabled', {
          configurable: trackEnabledPropertyDescriptor.configurable,
          enumerable: trackEnabledPropertyDescriptor.enumerable,
          get: function () {
              return trackEnabledPropertyDescriptor.get.bind(this)()
          },
          set: function (value) {
              const result = trackEnabledPropertyDescriptor.set.bind(this)(...arguments);
              if (videoTracks.has(this)) {
                  signalVideoTracksState();
              } else if (audioTracks.has(this)) {
                  signalAudioTracksState();
              }
              return result
          }
      });

      // proxy for get*Tracks methods -> needed to monitor tracks returned by saved media stream coming for MediaDevices.getUserMedia
      const getTracksMethodNames = ['getTracks', 'getAudioTracks', 'getVideoTracks'];
      for (const methodName of getTracksMethodNames) {
          const getTracksProxy = new DDGProxy(featureName, MediaStream.prototype, methodName, {
              apply (target, thisArg, args) {
                  const tracks = DDGReflect.apply(target, thisArg, args);
                  if (userMediaStreams.has(thisArg)) {
                      tracks.forEach(monitorTrack);
                  }
                  return tracks
              }
          });
          getTracksProxy.overload();
      }

      // proxy for MediaStream.clone -> needed to monitor cloned MediaDevices.getUserMedia streams
      const cloneMediaStreamProxy = new DDGProxy(featureName, MediaStream.prototype, 'clone', {
          apply (target, thisArg, args) {
              const clonedStream = DDGReflect.apply(target, thisArg, args);
              if (userMediaStreams.has(thisArg)) {
                  console.debug(`User stream ${thisArg.id} has been cloned to stream ${clonedStream.id}`);
                  userMediaStreams.add(clonedStream);
              }
              return clonedStream
          }
      });
      cloneMediaStreamProxy.overload();

      // proxy for navigator.mediaDevices.getUserMedia -> show red camera/mic indicators
      if (MediaDevices) {
          const getUserMediaProxy = new DDGProxy(featureName, MediaDevices.prototype, 'getUserMedia', {
              apply (target, thisArg, args) {
                  if (isFrameInsideFrame) {
                      // we can't communicate with iframes inside iframes -> deny permission instead of putting users at risk
                      return Promise.reject(new DOMException('Permission denied'))
                  }

                  const videoRequested = args[0]?.video;
                  const audioRequested = args[0]?.audio;
                  return DDGReflect.apply(target, thisArg, args).then(function (stream) {
                      console.debug(`User stream ${stream.id} has been acquired`);
                      userMediaStreams.add(stream);
                      if (videoRequested) {
                          const newVideoTracks = stream.getVideoTracks();
                          if (newVideoTracks?.length > 0) {
                              signalPermissionStatus(Permission.Camera, Status.Active);
                          }
                          newVideoTracks.forEach(monitorTrack);
                      }

                      if (audioRequested) {
                          const newAudioTracks = stream.getAudioTracks();
                          if (newAudioTracks?.length > 0) {
                              signalPermissionStatus(Permission.Microphone, Status.Active);
                          }
                          newAudioTracks.forEach(monitorTrack);
                      }
                      return stream
                  })
              }
          });
          getUserMediaProxy.overload();
      }

      function performAction (action, permission) {
          if (action && permission) {
              switch (action) {
              case 'pause':
                  pause(permission);
                  break
              case 'resume':
                  resume(permission);
                  break
              case 'stop':
                  stop(permission);
                  break
              }
          }
      }

      // handle actions from browser
      window.chrome.webview.addEventListener('message', function ({ data }) {
          if (data?.action && data?.permission) {
              performAction(data?.action, data?.permission);
          }
      });

      // these permissions cannot be disabled using WebView2 or DevTools protocol
      const permissionsToDisable = [
          { name: 'Bluetooth', prototype: Bluetooth.prototype, method: 'requestDevice' },
          { name: 'USB', prototype: USB.prototype, method: 'requestDevice' },
          { name: 'Serial', prototype: Serial.prototype, method: 'requestPort' },
          { name: 'HID', prototype: HID.prototype, method: 'requestDevice' }
      ];
      for (const { name, prototype, method } of permissionsToDisable) {
          try {
              const proxy = new DDGProxy(featureName, prototype, method, {
                  apply () {
                      return Promise.reject(new DOMException('Permission denied'))
                  }
              });
              proxy.overload();
          } catch (error) {
              console.info(`Could not disable access to ${name} because of error`, error);
          }
      }
  }

  var windowsPermissionUsage = /*#__PURE__*/Object.freeze({
    __proto__: null,
    init: init
  });

  exports.init = init$d;
  exports.load = load$1;
  exports.update = update$1;

  Object.defineProperty(exports, '__esModule', { value: true });

  return exports;

})({});


  function init () {
      const processedConfig = processConfig($CONTENT_SCOPE$, $USER_UNPROTECTED_DOMAINS$, $USER_PREFERENCES$);
      if (processedConfig.site.allowlisted) {
          return
      }

      contentScopeFeatures.load();

      contentScopeFeatures.init(processedConfig);

      // Not supported:
      // contentScopeFeatures.update(message)
  }

  init();

})();

