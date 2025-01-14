"use strict";
(() => {
  var __defProp = Object.defineProperty;
  var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
  var __publicField = (obj, key, value) => {
    __defNormalProp(obj, typeof key !== "symbol" ? key + "" : key, value);
    return value;
  };

  // node_modules/@lit/reactive-element/css-tag.js
  var t = globalThis;
  var e = t.ShadowRoot && (void 0 === t.ShadyCSS || t.ShadyCSS.nativeShadow) && "adoptedStyleSheets" in Document.prototype && "replace" in CSSStyleSheet.prototype;
  var s = Symbol();
  var o = /* @__PURE__ */ new WeakMap();
  var n = class {
    constructor(t4, e6, o5) {
      if (this._$cssResult$ = true, o5 !== s)
        throw Error("CSSResult is not constructable. Use `unsafeCSS` or `css` instead.");
      this.cssText = t4, this.t = e6;
    }
    get styleSheet() {
      let t4 = this.o;
      const s2 = this.t;
      if (e && void 0 === t4) {
        const e6 = void 0 !== s2 && 1 === s2.length;
        e6 && (t4 = o.get(s2)), void 0 === t4 && ((this.o = t4 = new CSSStyleSheet()).replaceSync(this.cssText), e6 && o.set(s2, t4));
      }
      return t4;
    }
    toString() {
      return this.cssText;
    }
  };
  var r = (t4) => new n("string" == typeof t4 ? t4 : t4 + "", void 0, s);
  var i = (t4, ...e6) => {
    const o5 = 1 === t4.length ? t4[0] : e6.reduce((e7, s2, o6) => e7 + ((t5) => {
      if (true === t5._$cssResult$)
        return t5.cssText;
      if ("number" == typeof t5)
        return t5;
      throw Error("Value passed to 'css' function must be a 'css' function result: " + t5 + ". Use 'unsafeCSS' to pass non-literal values, but take care to ensure page security.");
    })(s2) + t4[o6 + 1], t4[0]);
    return new n(o5, t4, s);
  };
  var S = (s2, o5) => {
    if (e)
      s2.adoptedStyleSheets = o5.map((t4) => t4 instanceof CSSStyleSheet ? t4 : t4.styleSheet);
    else
      for (const e6 of o5) {
        const o6 = document.createElement("style"), n5 = t.litNonce;
        void 0 !== n5 && o6.setAttribute("nonce", n5), o6.textContent = e6.cssText, s2.appendChild(o6);
      }
  };
  var c = e ? (t4) => t4 : (t4) => t4 instanceof CSSStyleSheet ? ((t5) => {
    let e6 = "";
    for (const s2 of t5.cssRules)
      e6 += s2.cssText;
    return r(e6);
  })(t4) : t4;

  // node_modules/@lit/reactive-element/reactive-element.js
  var { is: i2, defineProperty: e2, getOwnPropertyDescriptor: r2, getOwnPropertyNames: h, getOwnPropertySymbols: o2, getPrototypeOf: n2 } = Object;
  var a = globalThis;
  var c2 = a.trustedTypes;
  var l = c2 ? c2.emptyScript : "";
  var p = a.reactiveElementPolyfillSupport;
  var d = (t4, s2) => t4;
  var u = { toAttribute(t4, s2) {
    switch (s2) {
      case Boolean:
        t4 = t4 ? l : null;
        break;
      case Object:
      case Array:
        t4 = null == t4 ? t4 : JSON.stringify(t4);
    }
    return t4;
  }, fromAttribute(t4, s2) {
    let i4 = t4;
    switch (s2) {
      case Boolean:
        i4 = null !== t4;
        break;
      case Number:
        i4 = null === t4 ? null : Number(t4);
        break;
      case Object:
      case Array:
        try {
          i4 = JSON.parse(t4);
        } catch (t5) {
          i4 = null;
        }
    }
    return i4;
  } };
  var f = (t4, s2) => !i2(t4, s2);
  var y = { attribute: true, type: String, converter: u, reflect: false, hasChanged: f };
  Symbol.metadata ??= Symbol("metadata"), a.litPropertyMetadata ??= /* @__PURE__ */ new WeakMap();
  var b = class extends HTMLElement {
    static addInitializer(t4) {
      this._$Ei(), (this.l ??= []).push(t4);
    }
    static get observedAttributes() {
      return this.finalize(), this._$Eh && [...this._$Eh.keys()];
    }
    static createProperty(t4, s2 = y) {
      if (s2.state && (s2.attribute = false), this._$Ei(), this.elementProperties.set(t4, s2), !s2.noAccessor) {
        const i4 = Symbol(), r5 = this.getPropertyDescriptor(t4, i4, s2);
        void 0 !== r5 && e2(this.prototype, t4, r5);
      }
    }
    static getPropertyDescriptor(t4, s2, i4) {
      const { get: e6, set: h4 } = r2(this.prototype, t4) ?? { get() {
        return this[s2];
      }, set(t5) {
        this[s2] = t5;
      } };
      return { get() {
        return e6?.call(this);
      }, set(s3) {
        const r5 = e6?.call(this);
        h4.call(this, s3), this.requestUpdate(t4, r5, i4);
      }, configurable: true, enumerable: true };
    }
    static getPropertyOptions(t4) {
      return this.elementProperties.get(t4) ?? y;
    }
    static _$Ei() {
      if (this.hasOwnProperty(d("elementProperties")))
        return;
      const t4 = n2(this);
      t4.finalize(), void 0 !== t4.l && (this.l = [...t4.l]), this.elementProperties = new Map(t4.elementProperties);
    }
    static finalize() {
      if (this.hasOwnProperty(d("finalized")))
        return;
      if (this.finalized = true, this._$Ei(), this.hasOwnProperty(d("properties"))) {
        const t5 = this.properties, s2 = [...h(t5), ...o2(t5)];
        for (const i4 of s2)
          this.createProperty(i4, t5[i4]);
      }
      const t4 = this[Symbol.metadata];
      if (null !== t4) {
        const s2 = litPropertyMetadata.get(t4);
        if (void 0 !== s2)
          for (const [t5, i4] of s2)
            this.elementProperties.set(t5, i4);
      }
      this._$Eh = /* @__PURE__ */ new Map();
      for (const [t5, s2] of this.elementProperties) {
        const i4 = this._$Eu(t5, s2);
        void 0 !== i4 && this._$Eh.set(i4, t5);
      }
      this.elementStyles = this.finalizeStyles(this.styles);
    }
    static finalizeStyles(s2) {
      const i4 = [];
      if (Array.isArray(s2)) {
        const e6 = new Set(s2.flat(1 / 0).reverse());
        for (const s3 of e6)
          i4.unshift(c(s3));
      } else
        void 0 !== s2 && i4.push(c(s2));
      return i4;
    }
    static _$Eu(t4, s2) {
      const i4 = s2.attribute;
      return false === i4 ? void 0 : "string" == typeof i4 ? i4 : "string" == typeof t4 ? t4.toLowerCase() : void 0;
    }
    constructor() {
      super(), this._$Ep = void 0, this.isUpdatePending = false, this.hasUpdated = false, this._$Em = null, this._$Ev();
    }
    _$Ev() {
      this._$ES = new Promise((t4) => this.enableUpdating = t4), this._$AL = /* @__PURE__ */ new Map(), this._$E_(), this.requestUpdate(), this.constructor.l?.forEach((t4) => t4(this));
    }
    addController(t4) {
      (this._$EO ??= /* @__PURE__ */ new Set()).add(t4), void 0 !== this.renderRoot && this.isConnected && t4.hostConnected?.();
    }
    removeController(t4) {
      this._$EO?.delete(t4);
    }
    _$E_() {
      const t4 = /* @__PURE__ */ new Map(), s2 = this.constructor.elementProperties;
      for (const i4 of s2.keys())
        this.hasOwnProperty(i4) && (t4.set(i4, this[i4]), delete this[i4]);
      t4.size > 0 && (this._$Ep = t4);
    }
    createRenderRoot() {
      const t4 = this.shadowRoot ?? this.attachShadow(this.constructor.shadowRootOptions);
      return S(t4, this.constructor.elementStyles), t4;
    }
    connectedCallback() {
      this.renderRoot ??= this.createRenderRoot(), this.enableUpdating(true), this._$EO?.forEach((t4) => t4.hostConnected?.());
    }
    enableUpdating(t4) {
    }
    disconnectedCallback() {
      this._$EO?.forEach((t4) => t4.hostDisconnected?.());
    }
    attributeChangedCallback(t4, s2, i4) {
      this._$AK(t4, i4);
    }
    _$EC(t4, s2) {
      const i4 = this.constructor.elementProperties.get(t4), e6 = this.constructor._$Eu(t4, i4);
      if (void 0 !== e6 && true === i4.reflect) {
        const r5 = (void 0 !== i4.converter?.toAttribute ? i4.converter : u).toAttribute(s2, i4.type);
        this._$Em = t4, null == r5 ? this.removeAttribute(e6) : this.setAttribute(e6, r5), this._$Em = null;
      }
    }
    _$AK(t4, s2) {
      const i4 = this.constructor, e6 = i4._$Eh.get(t4);
      if (void 0 !== e6 && this._$Em !== e6) {
        const t5 = i4.getPropertyOptions(e6), r5 = "function" == typeof t5.converter ? { fromAttribute: t5.converter } : void 0 !== t5.converter?.fromAttribute ? t5.converter : u;
        this._$Em = e6, this[e6] = r5.fromAttribute(s2, t5.type), this._$Em = null;
      }
    }
    requestUpdate(t4, s2, i4) {
      if (void 0 !== t4) {
        if (i4 ??= this.constructor.getPropertyOptions(t4), !(i4.hasChanged ?? f)(this[t4], s2))
          return;
        this.P(t4, s2, i4);
      }
      false === this.isUpdatePending && (this._$ES = this._$ET());
    }
    P(t4, s2, i4) {
      this._$AL.has(t4) || this._$AL.set(t4, s2), true === i4.reflect && this._$Em !== t4 && (this._$Ej ??= /* @__PURE__ */ new Set()).add(t4);
    }
    async _$ET() {
      this.isUpdatePending = true;
      try {
        await this._$ES;
      } catch (t5) {
        Promise.reject(t5);
      }
      const t4 = this.scheduleUpdate();
      return null != t4 && await t4, !this.isUpdatePending;
    }
    scheduleUpdate() {
      return this.performUpdate();
    }
    performUpdate() {
      if (!this.isUpdatePending)
        return;
      if (!this.hasUpdated) {
        if (this.renderRoot ??= this.createRenderRoot(), this._$Ep) {
          for (const [t6, s3] of this._$Ep)
            this[t6] = s3;
          this._$Ep = void 0;
        }
        const t5 = this.constructor.elementProperties;
        if (t5.size > 0)
          for (const [s3, i4] of t5)
            true !== i4.wrapped || this._$AL.has(s3) || void 0 === this[s3] || this.P(s3, this[s3], i4);
      }
      let t4 = false;
      const s2 = this._$AL;
      try {
        t4 = this.shouldUpdate(s2), t4 ? (this.willUpdate(s2), this._$EO?.forEach((t5) => t5.hostUpdate?.()), this.update(s2)) : this._$EU();
      } catch (s3) {
        throw t4 = false, this._$EU(), s3;
      }
      t4 && this._$AE(s2);
    }
    willUpdate(t4) {
    }
    _$AE(t4) {
      this._$EO?.forEach((t5) => t5.hostUpdated?.()), this.hasUpdated || (this.hasUpdated = true, this.firstUpdated(t4)), this.updated(t4);
    }
    _$EU() {
      this._$AL = /* @__PURE__ */ new Map(), this.isUpdatePending = false;
    }
    get updateComplete() {
      return this.getUpdateComplete();
    }
    getUpdateComplete() {
      return this._$ES;
    }
    shouldUpdate(t4) {
      return true;
    }
    update(t4) {
      this._$Ej &&= this._$Ej.forEach((t5) => this._$EC(t5, this[t5])), this._$EU();
    }
    updated(t4) {
    }
    firstUpdated(t4) {
    }
  };
  b.elementStyles = [], b.shadowRootOptions = { mode: "open" }, b[d("elementProperties")] = /* @__PURE__ */ new Map(), b[d("finalized")] = /* @__PURE__ */ new Map(), p?.({ ReactiveElement: b }), (a.reactiveElementVersions ??= []).push("2.0.4");

  // node_modules/lit-html/lit-html.js
  var n3 = globalThis;
  var c3 = n3.trustedTypes;
  var h2 = c3 ? c3.createPolicy("lit-html", { createHTML: (t4) => t4 }) : void 0;
  var f2 = "$lit$";
  var v = `lit$${Math.random().toFixed(9).slice(2)}$`;
  var m = "?" + v;
  var _ = `<${m}>`;
  var w = document;
  var lt = () => w.createComment("");
  var st = (t4) => null === t4 || "object" != typeof t4 && "function" != typeof t4;
  var g = Array.isArray;
  var $ = (t4) => g(t4) || "function" == typeof t4?.[Symbol.iterator];
  var x = "[ 	\n\f\r]";
  var T = /<(?:(!--|\/[^a-zA-Z])|(\/?[a-zA-Z][^>\s]*)|(\/?$))/g;
  var E = /-->/g;
  var k = />/g;
  var O = RegExp(`>|${x}(?:([^\\s"'>=/]+)(${x}*=${x}*(?:[^ 	
\f\r"'\`<>=]|("|')|))|$)`, "g");
  var S2 = /'/g;
  var j = /"/g;
  var M = /^(?:script|style|textarea|title)$/i;
  var P = (t4) => (i4, ...s2) => ({ _$litType$: t4, strings: i4, values: s2 });
  var ke = P(1);
  var Oe = P(2);
  var Se = P(3);
  var R = Symbol.for("lit-noChange");
  var D = Symbol.for("lit-nothing");
  var V = /* @__PURE__ */ new WeakMap();
  var I = w.createTreeWalker(w, 129);
  function N(t4, i4) {
    if (!g(t4) || !t4.hasOwnProperty("raw"))
      throw Error("invalid template strings array");
    return void 0 !== h2 ? h2.createHTML(i4) : i4;
  }
  var U = (t4, i4) => {
    const s2 = t4.length - 1, e6 = [];
    let h4, o5 = 2 === i4 ? "<svg>" : 3 === i4 ? "<math>" : "", n5 = T;
    for (let i5 = 0; i5 < s2; i5++) {
      const s3 = t4[i5];
      let r5, l2, c4 = -1, a2 = 0;
      for (; a2 < s3.length && (n5.lastIndex = a2, l2 = n5.exec(s3), null !== l2); )
        a2 = n5.lastIndex, n5 === T ? "!--" === l2[1] ? n5 = E : void 0 !== l2[1] ? n5 = k : void 0 !== l2[2] ? (M.test(l2[2]) && (h4 = RegExp("</" + l2[2], "g")), n5 = O) : void 0 !== l2[3] && (n5 = O) : n5 === O ? ">" === l2[0] ? (n5 = h4 ?? T, c4 = -1) : void 0 === l2[1] ? c4 = -2 : (c4 = n5.lastIndex - l2[2].length, r5 = l2[1], n5 = void 0 === l2[3] ? O : '"' === l2[3] ? j : S2) : n5 === j || n5 === S2 ? n5 = O : n5 === E || n5 === k ? n5 = T : (n5 = O, h4 = void 0);
      const u2 = n5 === O && t4[i5 + 1].startsWith("/>") ? " " : "";
      o5 += n5 === T ? s3 + _ : c4 >= 0 ? (e6.push(r5), s3.slice(0, c4) + f2 + s3.slice(c4) + v + u2) : s3 + v + (-2 === c4 ? i5 : u2);
    }
    return [N(t4, o5 + (t4[s2] || "<?>") + (2 === i4 ? "</svg>" : 3 === i4 ? "</math>" : "")), e6];
  };
  var B = class _B {
    constructor({ strings: t4, _$litType$: i4 }, s2) {
      let e6;
      this.parts = [];
      let h4 = 0, o5 = 0;
      const n5 = t4.length - 1, r5 = this.parts, [l2, a2] = U(t4, i4);
      if (this.el = _B.createElement(l2, s2), I.currentNode = this.el.content, 2 === i4 || 3 === i4) {
        const t5 = this.el.content.firstChild;
        t5.replaceWith(...t5.childNodes);
      }
      for (; null !== (e6 = I.nextNode()) && r5.length < n5; ) {
        if (1 === e6.nodeType) {
          if (e6.hasAttributes())
            for (const t5 of e6.getAttributeNames())
              if (t5.endsWith(f2)) {
                const i5 = a2[o5++], s3 = e6.getAttribute(t5).split(v), n6 = /([.?@])?(.*)/.exec(i5);
                r5.push({ type: 1, index: h4, name: n6[2], strings: s3, ctor: "." === n6[1] ? Y : "?" === n6[1] ? Z : "@" === n6[1] ? q : G }), e6.removeAttribute(t5);
              } else
                t5.startsWith(v) && (r5.push({ type: 6, index: h4 }), e6.removeAttribute(t5));
          if (M.test(e6.tagName)) {
            const t5 = e6.textContent.split(v), i5 = t5.length - 1;
            if (i5 > 0) {
              e6.textContent = c3 ? c3.emptyScript : "";
              for (let s3 = 0; s3 < i5; s3++)
                e6.append(t5[s3], lt()), I.nextNode(), r5.push({ type: 2, index: ++h4 });
              e6.append(t5[i5], lt());
            }
          }
        } else if (8 === e6.nodeType)
          if (e6.data === m)
            r5.push({ type: 2, index: h4 });
          else {
            let t5 = -1;
            for (; -1 !== (t5 = e6.data.indexOf(v, t5 + 1)); )
              r5.push({ type: 7, index: h4 }), t5 += v.length - 1;
          }
        h4++;
      }
    }
    static createElement(t4, i4) {
      const s2 = w.createElement("template");
      return s2.innerHTML = t4, s2;
    }
  };
  function z(t4, i4, s2 = t4, e6) {
    if (i4 === R)
      return i4;
    let h4 = void 0 !== e6 ? s2.o?.[e6] : s2.l;
    const o5 = st(i4) ? void 0 : i4._$litDirective$;
    return h4?.constructor !== o5 && (h4?._$AO?.(false), void 0 === o5 ? h4 = void 0 : (h4 = new o5(t4), h4._$AT(t4, s2, e6)), void 0 !== e6 ? (s2.o ??= [])[e6] = h4 : s2.l = h4), void 0 !== h4 && (i4 = z(t4, h4._$AS(t4, i4.values), h4, e6)), i4;
  }
  var F = class {
    constructor(t4, i4) {
      this._$AV = [], this._$AN = void 0, this._$AD = t4, this._$AM = i4;
    }
    get parentNode() {
      return this._$AM.parentNode;
    }
    get _$AU() {
      return this._$AM._$AU;
    }
    u(t4) {
      const { el: { content: i4 }, parts: s2 } = this._$AD, e6 = (t4?.creationScope ?? w).importNode(i4, true);
      I.currentNode = e6;
      let h4 = I.nextNode(), o5 = 0, n5 = 0, r5 = s2[0];
      for (; void 0 !== r5; ) {
        if (o5 === r5.index) {
          let i5;
          2 === r5.type ? i5 = new et(h4, h4.nextSibling, this, t4) : 1 === r5.type ? i5 = new r5.ctor(h4, r5.name, r5.strings, this, t4) : 6 === r5.type && (i5 = new K(h4, this, t4)), this._$AV.push(i5), r5 = s2[++n5];
        }
        o5 !== r5?.index && (h4 = I.nextNode(), o5++);
      }
      return I.currentNode = w, e6;
    }
    p(t4) {
      let i4 = 0;
      for (const s2 of this._$AV)
        void 0 !== s2 && (void 0 !== s2.strings ? (s2._$AI(t4, s2, i4), i4 += s2.strings.length - 2) : s2._$AI(t4[i4])), i4++;
    }
  };
  var et = class _et {
    get _$AU() {
      return this._$AM?._$AU ?? this.v;
    }
    constructor(t4, i4, s2, e6) {
      this.type = 2, this._$AH = D, this._$AN = void 0, this._$AA = t4, this._$AB = i4, this._$AM = s2, this.options = e6, this.v = e6?.isConnected ?? true;
    }
    get parentNode() {
      let t4 = this._$AA.parentNode;
      const i4 = this._$AM;
      return void 0 !== i4 && 11 === t4?.nodeType && (t4 = i4.parentNode), t4;
    }
    get startNode() {
      return this._$AA;
    }
    get endNode() {
      return this._$AB;
    }
    _$AI(t4, i4 = this) {
      t4 = z(this, t4, i4), st(t4) ? t4 === D || null == t4 || "" === t4 ? (this._$AH !== D && this._$AR(), this._$AH = D) : t4 !== this._$AH && t4 !== R && this._(t4) : void 0 !== t4._$litType$ ? this.$(t4) : void 0 !== t4.nodeType ? this.T(t4) : $(t4) ? this.k(t4) : this._(t4);
    }
    O(t4) {
      return this._$AA.parentNode.insertBefore(t4, this._$AB);
    }
    T(t4) {
      this._$AH !== t4 && (this._$AR(), this._$AH = this.O(t4));
    }
    _(t4) {
      this._$AH !== D && st(this._$AH) ? this._$AA.nextSibling.data = t4 : this.T(w.createTextNode(t4)), this._$AH = t4;
    }
    $(t4) {
      const { values: i4, _$litType$: s2 } = t4, e6 = "number" == typeof s2 ? this._$AC(t4) : (void 0 === s2.el && (s2.el = B.createElement(N(s2.h, s2.h[0]), this.options)), s2);
      if (this._$AH?._$AD === e6)
        this._$AH.p(i4);
      else {
        const t5 = new F(e6, this), s3 = t5.u(this.options);
        t5.p(i4), this.T(s3), this._$AH = t5;
      }
    }
    _$AC(t4) {
      let i4 = V.get(t4.strings);
      return void 0 === i4 && V.set(t4.strings, i4 = new B(t4)), i4;
    }
    k(t4) {
      g(this._$AH) || (this._$AH = [], this._$AR());
      const i4 = this._$AH;
      let s2, e6 = 0;
      for (const h4 of t4)
        e6 === i4.length ? i4.push(s2 = new _et(this.O(lt()), this.O(lt()), this, this.options)) : s2 = i4[e6], s2._$AI(h4), e6++;
      e6 < i4.length && (this._$AR(s2 && s2._$AB.nextSibling, e6), i4.length = e6);
    }
    _$AR(t4 = this._$AA.nextSibling, i4) {
      for (this._$AP?.(false, true, i4); t4 && t4 !== this._$AB; ) {
        const i5 = t4.nextSibling;
        t4.remove(), t4 = i5;
      }
    }
    setConnected(t4) {
      void 0 === this._$AM && (this.v = t4, this._$AP?.(t4));
    }
  };
  var G = class {
    get tagName() {
      return this.element.tagName;
    }
    get _$AU() {
      return this._$AM._$AU;
    }
    constructor(t4, i4, s2, e6, h4) {
      this.type = 1, this._$AH = D, this._$AN = void 0, this.element = t4, this.name = i4, this._$AM = e6, this.options = h4, s2.length > 2 || "" !== s2[0] || "" !== s2[1] ? (this._$AH = Array(s2.length - 1).fill(new String()), this.strings = s2) : this._$AH = D;
    }
    _$AI(t4, i4 = this, s2, e6) {
      const h4 = this.strings;
      let o5 = false;
      if (void 0 === h4)
        t4 = z(this, t4, i4, 0), o5 = !st(t4) || t4 !== this._$AH && t4 !== R, o5 && (this._$AH = t4);
      else {
        const e7 = t4;
        let n5, r5;
        for (t4 = h4[0], n5 = 0; n5 < h4.length - 1; n5++)
          r5 = z(this, e7[s2 + n5], i4, n5), r5 === R && (r5 = this._$AH[n5]), o5 ||= !st(r5) || r5 !== this._$AH[n5], r5 === D ? t4 = D : t4 !== D && (t4 += (r5 ?? "") + h4[n5 + 1]), this._$AH[n5] = r5;
      }
      o5 && !e6 && this.j(t4);
    }
    j(t4) {
      t4 === D ? this.element.removeAttribute(this.name) : this.element.setAttribute(this.name, t4 ?? "");
    }
  };
  var Y = class extends G {
    constructor() {
      super(...arguments), this.type = 3;
    }
    j(t4) {
      this.element[this.name] = t4 === D ? void 0 : t4;
    }
  };
  var Z = class extends G {
    constructor() {
      super(...arguments), this.type = 4;
    }
    j(t4) {
      this.element.toggleAttribute(this.name, !!t4 && t4 !== D);
    }
  };
  var q = class extends G {
    constructor(t4, i4, s2, e6, h4) {
      super(t4, i4, s2, e6, h4), this.type = 5;
    }
    _$AI(t4, i4 = this) {
      if ((t4 = z(this, t4, i4, 0) ?? D) === R)
        return;
      const s2 = this._$AH, e6 = t4 === D && s2 !== D || t4.capture !== s2.capture || t4.once !== s2.once || t4.passive !== s2.passive, h4 = t4 !== D && (s2 === D || e6);
      e6 && this.element.removeEventListener(this.name, this, s2), h4 && this.element.addEventListener(this.name, this, t4), this._$AH = t4;
    }
    handleEvent(t4) {
      "function" == typeof this._$AH ? this._$AH.call(this.options?.host ?? this.element, t4) : this._$AH.handleEvent(t4);
    }
  };
  var K = class {
    constructor(t4, i4, s2) {
      this.element = t4, this.type = 6, this._$AN = void 0, this._$AM = i4, this.options = s2;
    }
    get _$AU() {
      return this._$AM._$AU;
    }
    _$AI(t4) {
      z(this, t4);
    }
  };
  var si = { M: f2, P: v, A: m, C: 1, L: U, R: F, D: $, V: z, I: et, H: G, N: Z, U: q, B: Y, F: K };
  var Re = n3.litHtmlPolyfillSupport;
  Re?.(B, et), (n3.litHtmlVersions ??= []).push("3.2.0");
  var Q = (t4, i4, s2) => {
    const e6 = s2?.renderBefore ?? i4;
    let h4 = e6._$litPart$;
    if (void 0 === h4) {
      const t5 = s2?.renderBefore ?? null;
      e6._$litPart$ = h4 = new et(i4.insertBefore(lt(), t5), t5, void 0, s2 ?? {});
    }
    return h4._$AI(t4), h4;
  };

  // node_modules/lit-element/lit-element.js
  var h3 = class extends b {
    constructor() {
      super(...arguments), this.renderOptions = { host: this }, this.o = void 0;
    }
    createRenderRoot() {
      const t4 = super.createRenderRoot();
      return this.renderOptions.renderBefore ??= t4.firstChild, t4;
    }
    update(t4) {
      const e6 = this.render();
      this.hasUpdated || (this.renderOptions.isConnected = this.isConnected), super.update(t4), this.o = Q(e6, this.renderRoot, this.renderOptions);
    }
    connectedCallback() {
      super.connectedCallback(), this.o?.setConnected(true);
    }
    disconnectedCallback() {
      super.disconnectedCallback(), this.o?.setConnected(false);
    }
    render() {
      return R;
    }
  };
  h3._$litElement$ = true, h3["finalized"] = true, globalThis.litElementHydrateSupport?.({ LitElement: h3 });
  var f3 = globalThis.litElementPolyfillSupport;
  f3?.({ LitElement: h3 });
  (globalThis.litElementVersions ??= []).push("4.1.0");

  // node_modules/lit-html/is-server.js
  var co = false;

  // node_modules/lit-html/directives/map.js
  function* oo(o5, f4) {
    if (void 0 !== o5) {
      let i4 = 0;
      for (const t4 of o5)
        yield f4(t4, i4++);
    }
  }

  // node_modules/tslib/tslib.es6.js
  function __decorate(decorators, target, key, desc) {
    var c4 = arguments.length, r5 = c4 < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d2;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function")
      r5 = Reflect.decorate(decorators, target, key, desc);
    else
      for (var i4 = decorators.length - 1; i4 >= 0; i4--)
        if (d2 = decorators[i4])
          r5 = (c4 < 3 ? d2(r5) : c4 > 3 ? d2(target, key, r5) : d2(target, key)) || r5;
    return c4 > 3 && r5 && Object.defineProperty(target, key, r5), r5;
  }

  // node_modules/@lit/reactive-element/decorators/custom-element.js
  var t2 = (t4) => (e6, o5) => {
    void 0 !== o5 ? o5.addInitializer(() => {
      customElements.define(t4, e6);
    }) : customElements.define(t4, e6);
  };

  // node_modules/@lit/reactive-element/decorators/property.js
  var o3 = { attribute: true, type: String, converter: u, reflect: false, hasChanged: f };
  var r3 = (t4 = o3, e6, r5) => {
    const { kind: n5, metadata: i4 } = r5;
    let s2 = globalThis.litPropertyMetadata.get(i4);
    if (void 0 === s2 && globalThis.litPropertyMetadata.set(i4, s2 = /* @__PURE__ */ new Map()), s2.set(r5.name, t4), "accessor" === n5) {
      const { name: o5 } = r5;
      return { set(r6) {
        const n6 = e6.get.call(this);
        e6.set.call(this, r6), this.requestUpdate(o5, n6, t4);
      }, init(e7) {
        return void 0 !== e7 && this.P(o5, void 0, t4), e7;
      } };
    }
    if ("setter" === n5) {
      const { name: o5 } = r5;
      return function(r6) {
        const n6 = this[o5];
        e6.call(this, r6), this.requestUpdate(o5, n6, t4);
      };
    }
    throw Error("Unsupported decorator location: " + n5);
  };
  function n4(t4) {
    return (e6, o5) => "object" == typeof o5 ? r3(t4, e6, o5) : ((t5, e7, o6) => {
      const r5 = e7.hasOwnProperty(o6);
      return e7.constructor.createProperty(o6, r5 ? { ...t5, wrapped: true } : t5), r5 ? Object.getOwnPropertyDescriptor(e7, o6) : void 0;
    })(t4, e6, o5);
  }

  // node_modules/@lit/reactive-element/decorators/state.js
  function r4(r5) {
    return n4({ ...r5, state: true, attribute: false });
  }

  // node_modules/@lit/reactive-element/decorators/base.js
  var e3 = (e6, t4, c4) => (c4.configurable = true, c4.enumerable = true, Reflect.decorate && "object" != typeof t4 && Object.defineProperty(e6, t4, c4), c4);

  // node_modules/@lit/reactive-element/decorators/query.js
  function e4(e6, r5) {
    return (n5, s2, i4) => {
      const o5 = (t4) => t4.renderRoot?.querySelector(e6) ?? null;
      if (r5) {
        const { get: e7, set: r6 } = "object" == typeof s2 ? n5 : i4 ?? (() => {
          const t4 = Symbol();
          return { get() {
            return this[t4];
          }, set(e8) {
            this[t4] = e8;
          } };
        })();
        return e3(n5, s2, { get() {
          let t4 = e7.call(this);
          return void 0 === t4 && (t4 = o5(this), (null !== t4 || this.hasUpdated) && r6.call(this, t4)), t4;
        } });
      }
      return e3(n5, s2, { get() {
        return o5(this);
      } });
    };
  }

  // node_modules/@lit/reactive-element/decorators/query-assigned-elements.js
  function o4(o5) {
    return (e6, n5) => {
      const { slot: r5, selector: s2 } = o5 ?? {}, c4 = "slot" + (r5 ? `[name=${r5}]` : ":not([name])");
      return e3(e6, n5, { get() {
        const t4 = this.renderRoot?.querySelector(c4), e7 = t4?.assignedElements(o5) ?? [];
        return void 0 === s2 ? e7 : e7.filter((t5) => t5.matches(s2));
      } });
    };
  }

  // node_modules/@material/web/elevation/internal/elevation.js
  var Elevation = class extends h3 {
    connectedCallback() {
      super.connectedCallback();
      this.setAttribute("aria-hidden", "true");
    }
    render() {
      return ke`<span class="shadow"></span>`;
    }
  };

  // node_modules/@material/web/elevation/internal/elevation-styles.js
  var styles = i`:host,.shadow,.shadow::before,.shadow::after{border-radius:inherit;inset:0;position:absolute;transition-duration:inherit;transition-property:inherit;transition-timing-function:inherit}:host{display:flex;pointer-events:none;transition-property:box-shadow,opacity}.shadow::before,.shadow::after{content:"";transition-property:box-shadow,opacity;--_level: var(--md-elevation-level, 0);--_shadow-color: var(--md-elevation-shadow-color, var(--md-sys-color-shadow, #000))}.shadow::before{box-shadow:0px calc(1px*(clamp(0,var(--_level),1) + clamp(0,var(--_level) - 3,1) + 2*clamp(0,var(--_level) - 4,1))) calc(1px*(2*clamp(0,var(--_level),1) + clamp(0,var(--_level) - 2,1) + clamp(0,var(--_level) - 4,1))) 0px var(--_shadow-color);opacity:.3}.shadow::after{box-shadow:0px calc(1px*(clamp(0,var(--_level),1) + clamp(0,var(--_level) - 1,1) + 2*clamp(0,var(--_level) - 2,3))) calc(1px*(3*clamp(0,var(--_level),2) + 2*clamp(0,var(--_level) - 2,3))) calc(1px*(clamp(0,var(--_level),4) + 2*clamp(0,var(--_level) - 4,1))) var(--_shadow-color);opacity:.15}
`;

  // node_modules/@material/web/elevation/elevation.js
  var MdElevation = class MdElevation2 extends Elevation {
  };
  MdElevation.styles = [styles];
  MdElevation = __decorate([
    t2("md-elevation")
  ], MdElevation);

  // node_modules/@material/web/internal/controller/attachable-controller.js
  var ATTACHABLE_CONTROLLER = Symbol("attachableController");
  var FOR_ATTRIBUTE_OBSERVER;
  if (!co) {
    FOR_ATTRIBUTE_OBSERVER = new MutationObserver((records) => {
      for (const record of records) {
        record.target[ATTACHABLE_CONTROLLER]?.hostConnected();
      }
    });
  }
  var AttachableController = class {
    get htmlFor() {
      return this.host.getAttribute("for");
    }
    set htmlFor(htmlFor) {
      if (htmlFor === null) {
        this.host.removeAttribute("for");
      } else {
        this.host.setAttribute("for", htmlFor);
      }
    }
    get control() {
      if (this.host.hasAttribute("for")) {
        if (!this.htmlFor || !this.host.isConnected) {
          return null;
        }
        return this.host.getRootNode().querySelector(`#${this.htmlFor}`);
      }
      return this.currentControl || this.host.parentElement;
    }
    set control(control) {
      if (control) {
        this.attach(control);
      } else {
        this.detach();
      }
    }
    /**
     * Creates a new controller for an `Attachable` element.
     *
     * @param host The `Attachable` element.
     * @param onControlChange A callback with two parameters for the previous and
     *     next control. An `Attachable` element may perform setup or teardown
     *     logic whenever the control changes.
     */
    constructor(host, onControlChange) {
      this.host = host;
      this.onControlChange = onControlChange;
      this.currentControl = null;
      host.addController(this);
      host[ATTACHABLE_CONTROLLER] = this;
      FOR_ATTRIBUTE_OBSERVER?.observe(host, { attributeFilter: ["for"] });
    }
    attach(control) {
      if (control === this.currentControl) {
        return;
      }
      this.setCurrentControl(control);
      this.host.removeAttribute("for");
    }
    detach() {
      this.setCurrentControl(null);
      this.host.setAttribute("for", "");
    }
    /** @private */
    hostConnected() {
      this.setCurrentControl(this.control);
    }
    /** @private */
    hostDisconnected() {
      this.setCurrentControl(null);
    }
    setCurrentControl(control) {
      this.onControlChange(this.currentControl, control);
      this.currentControl = control;
    }
  };

  // node_modules/@material/web/focus/internal/focus-ring.js
  var EVENTS = ["focusin", "focusout", "pointerdown"];
  var FocusRing = class extends h3 {
    constructor() {
      super(...arguments);
      this.visible = false;
      this.inward = false;
      this.attachableController = new AttachableController(this, this.onControlChange.bind(this));
    }
    get htmlFor() {
      return this.attachableController.htmlFor;
    }
    set htmlFor(htmlFor) {
      this.attachableController.htmlFor = htmlFor;
    }
    get control() {
      return this.attachableController.control;
    }
    set control(control) {
      this.attachableController.control = control;
    }
    attach(control) {
      this.attachableController.attach(control);
    }
    detach() {
      this.attachableController.detach();
    }
    connectedCallback() {
      super.connectedCallback();
      this.setAttribute("aria-hidden", "true");
    }
    /** @private */
    handleEvent(event) {
      if (event[HANDLED_BY_FOCUS_RING]) {
        return;
      }
      switch (event.type) {
        default:
          return;
        case "focusin":
          this.visible = this.control?.matches(":focus-visible") ?? false;
          break;
        case "focusout":
        case "pointerdown":
          this.visible = false;
          break;
      }
      event[HANDLED_BY_FOCUS_RING] = true;
    }
    onControlChange(prev, next) {
      if (co)
        return;
      for (const event of EVENTS) {
        prev?.removeEventListener(event, this);
        next?.addEventListener(event, this);
      }
    }
    update(changed) {
      if (changed.has("visible")) {
        this.dispatchEvent(new Event("visibility-changed"));
      }
      super.update(changed);
    }
  };
  __decorate([
    n4({ type: Boolean, reflect: true })
  ], FocusRing.prototype, "visible", void 0);
  __decorate([
    n4({ type: Boolean, reflect: true })
  ], FocusRing.prototype, "inward", void 0);
  var HANDLED_BY_FOCUS_RING = Symbol("handledByFocusRing");

  // node_modules/@material/web/focus/internal/focus-ring-styles.js
  var styles2 = i`:host{animation-delay:0s,calc(var(--md-focus-ring-duration, 600ms)*.25);animation-duration:calc(var(--md-focus-ring-duration, 600ms)*.25),calc(var(--md-focus-ring-duration, 600ms)*.75);animation-timing-function:cubic-bezier(0.2, 0, 0, 1);box-sizing:border-box;color:var(--md-focus-ring-color, var(--md-sys-color-secondary, #625b71));display:none;pointer-events:none;position:absolute}:host([visible]){display:flex}:host(:not([inward])){animation-name:outward-grow,outward-shrink;border-end-end-radius:calc(var(--md-focus-ring-shape-end-end, var(--md-focus-ring-shape, var(--md-sys-shape-corner-full, 9999px))) + var(--md-focus-ring-outward-offset, 2px));border-end-start-radius:calc(var(--md-focus-ring-shape-end-start, var(--md-focus-ring-shape, var(--md-sys-shape-corner-full, 9999px))) + var(--md-focus-ring-outward-offset, 2px));border-start-end-radius:calc(var(--md-focus-ring-shape-start-end, var(--md-focus-ring-shape, var(--md-sys-shape-corner-full, 9999px))) + var(--md-focus-ring-outward-offset, 2px));border-start-start-radius:calc(var(--md-focus-ring-shape-start-start, var(--md-focus-ring-shape, var(--md-sys-shape-corner-full, 9999px))) + var(--md-focus-ring-outward-offset, 2px));inset:calc(-1*var(--md-focus-ring-outward-offset, 2px));outline:var(--md-focus-ring-width, 3px) solid currentColor}:host([inward]){animation-name:inward-grow,inward-shrink;border-end-end-radius:calc(var(--md-focus-ring-shape-end-end, var(--md-focus-ring-shape, var(--md-sys-shape-corner-full, 9999px))) - var(--md-focus-ring-inward-offset, 0px));border-end-start-radius:calc(var(--md-focus-ring-shape-end-start, var(--md-focus-ring-shape, var(--md-sys-shape-corner-full, 9999px))) - var(--md-focus-ring-inward-offset, 0px));border-start-end-radius:calc(var(--md-focus-ring-shape-start-end, var(--md-focus-ring-shape, var(--md-sys-shape-corner-full, 9999px))) - var(--md-focus-ring-inward-offset, 0px));border-start-start-radius:calc(var(--md-focus-ring-shape-start-start, var(--md-focus-ring-shape, var(--md-sys-shape-corner-full, 9999px))) - var(--md-focus-ring-inward-offset, 0px));border:var(--md-focus-ring-width, 3px) solid currentColor;inset:var(--md-focus-ring-inward-offset, 0px)}@keyframes outward-grow{from{outline-width:0}to{outline-width:var(--md-focus-ring-active-width, 8px)}}@keyframes outward-shrink{from{outline-width:var(--md-focus-ring-active-width, 8px)}}@keyframes inward-grow{from{border-width:0}to{border-width:var(--md-focus-ring-active-width, 8px)}}@keyframes inward-shrink{from{border-width:var(--md-focus-ring-active-width, 8px)}}@media(prefers-reduced-motion){:host{animation:none}}
`;

  // node_modules/@material/web/focus/md-focus-ring.js
  var MdFocusRing = class MdFocusRing2 extends FocusRing {
  };
  MdFocusRing.styles = [styles2];
  MdFocusRing = __decorate([
    t2("md-focus-ring")
  ], MdFocusRing);

  // node_modules/lit-html/directive.js
  var t3 = { ATTRIBUTE: 1, CHILD: 2, PROPERTY: 3, BOOLEAN_ATTRIBUTE: 4, EVENT: 5, ELEMENT: 6 };
  var e5 = (t4) => (...e6) => ({ _$litDirective$: t4, values: e6 });
  var i3 = class {
    constructor(t4) {
    }
    get _$AU() {
      return this._$AM._$AU;
    }
    _$AT(t4, e6, i4) {
      this.t = t4, this._$AM = e6, this.i = i4;
    }
    _$AS(t4, e6) {
      return this.update(t4, e6);
    }
    update(t4, e6) {
      return this.render(...e6);
    }
  };

  // node_modules/lit-html/directives/class-map.js
  var Rt = e5(class extends i3 {
    constructor(s2) {
      if (super(s2), s2.type !== t3.ATTRIBUTE || "class" !== s2.name || s2.strings?.length > 2)
        throw Error("`classMap()` can only be used in the `class` attribute and must be the only part in the attribute.");
    }
    render(t4) {
      return " " + Object.keys(t4).filter((s2) => t4[s2]).join(" ") + " ";
    }
    update(t4, [s2]) {
      if (void 0 === this.st) {
        this.st = /* @__PURE__ */ new Set(), void 0 !== t4.strings && (this.nt = new Set(t4.strings.join(" ").split(/\s/).filter((t5) => "" !== t5)));
        for (const t5 in s2)
          s2[t5] && !this.nt?.has(t5) && this.st.add(t5);
        return this.render(s2);
      }
      const i4 = t4.element.classList;
      for (const t5 of this.st)
        t5 in s2 || (i4.remove(t5), this.st.delete(t5));
      for (const t5 in s2) {
        const r5 = !!s2[t5];
        r5 === this.st.has(t5) || this.nt?.has(t5) || (r5 ? (i4.add(t5), this.st.add(t5)) : (i4.remove(t5), this.st.delete(t5)));
      }
      return R;
    }
  });

  // node_modules/@material/web/internal/motion/animation.js
  var EASING = {
    STANDARD: "cubic-bezier(0.2, 0, 0, 1)",
    STANDARD_ACCELERATE: "cubic-bezier(.3,0,1,1)",
    STANDARD_DECELERATE: "cubic-bezier(0,0,0,1)",
    EMPHASIZED: "cubic-bezier(.3,0,0,1)",
    EMPHASIZED_ACCELERATE: "cubic-bezier(.3,0,.8,.15)",
    EMPHASIZED_DECELERATE: "cubic-bezier(.05,.7,.1,1)"
  };

  // node_modules/@material/web/ripple/internal/ripple.js
  var PRESS_GROW_MS = 450;
  var MINIMUM_PRESS_MS = 225;
  var INITIAL_ORIGIN_SCALE = 0.2;
  var PADDING = 10;
  var SOFT_EDGE_MINIMUM_SIZE = 75;
  var SOFT_EDGE_CONTAINER_RATIO = 0.35;
  var PRESS_PSEUDO = "::after";
  var ANIMATION_FILL = "forwards";
  var State;
  (function(State2) {
    State2[State2["INACTIVE"] = 0] = "INACTIVE";
    State2[State2["TOUCH_DELAY"] = 1] = "TOUCH_DELAY";
    State2[State2["HOLDING"] = 2] = "HOLDING";
    State2[State2["WAITING_FOR_CLICK"] = 3] = "WAITING_FOR_CLICK";
  })(State || (State = {}));
  var EVENTS2 = [
    "click",
    "contextmenu",
    "pointercancel",
    "pointerdown",
    "pointerenter",
    "pointerleave",
    "pointerup"
  ];
  var TOUCH_DELAY_MS = 150;
  var FORCED_COLORS = co ? null : window.matchMedia("(forced-colors: active)");
  var Ripple = class extends h3 {
    constructor() {
      super(...arguments);
      this.disabled = false;
      this.hovered = false;
      this.pressed = false;
      this.rippleSize = "";
      this.rippleScale = "";
      this.initialSize = 0;
      this.state = State.INACTIVE;
      this.checkBoundsAfterContextMenu = false;
      this.attachableController = new AttachableController(this, this.onControlChange.bind(this));
    }
    get htmlFor() {
      return this.attachableController.htmlFor;
    }
    set htmlFor(htmlFor) {
      this.attachableController.htmlFor = htmlFor;
    }
    get control() {
      return this.attachableController.control;
    }
    set control(control) {
      this.attachableController.control = control;
    }
    attach(control) {
      this.attachableController.attach(control);
    }
    detach() {
      this.attachableController.detach();
    }
    connectedCallback() {
      super.connectedCallback();
      this.setAttribute("aria-hidden", "true");
    }
    render() {
      const classes = {
        "hovered": this.hovered,
        "pressed": this.pressed
      };
      return ke`<div class="surface ${Rt(classes)}"></div>`;
    }
    update(changedProps) {
      if (changedProps.has("disabled") && this.disabled) {
        this.hovered = false;
        this.pressed = false;
      }
      super.update(changedProps);
    }
    /**
     * TODO(b/269799771): make private
     * @private only public for slider
     */
    handlePointerenter(event) {
      if (!this.shouldReactToEvent(event)) {
        return;
      }
      this.hovered = true;
    }
    /**
     * TODO(b/269799771): make private
     * @private only public for slider
     */
    handlePointerleave(event) {
      if (!this.shouldReactToEvent(event)) {
        return;
      }
      this.hovered = false;
      if (this.state !== State.INACTIVE) {
        this.endPressAnimation();
      }
    }
    handlePointerup(event) {
      if (!this.shouldReactToEvent(event)) {
        return;
      }
      if (this.state === State.HOLDING) {
        this.state = State.WAITING_FOR_CLICK;
        return;
      }
      if (this.state === State.TOUCH_DELAY) {
        this.state = State.WAITING_FOR_CLICK;
        this.startPressAnimation(this.rippleStartEvent);
        return;
      }
    }
    async handlePointerdown(event) {
      if (!this.shouldReactToEvent(event)) {
        return;
      }
      this.rippleStartEvent = event;
      if (!this.isTouch(event)) {
        this.state = State.WAITING_FOR_CLICK;
        this.startPressAnimation(event);
        return;
      }
      if (this.checkBoundsAfterContextMenu && !this.inBounds(event)) {
        return;
      }
      this.checkBoundsAfterContextMenu = false;
      this.state = State.TOUCH_DELAY;
      await new Promise((resolve) => {
        setTimeout(resolve, TOUCH_DELAY_MS);
      });
      if (this.state !== State.TOUCH_DELAY) {
        return;
      }
      this.state = State.HOLDING;
      this.startPressAnimation(event);
    }
    handleClick() {
      if (this.disabled) {
        return;
      }
      if (this.state === State.WAITING_FOR_CLICK) {
        this.endPressAnimation();
        return;
      }
      if (this.state === State.INACTIVE) {
        this.startPressAnimation();
        this.endPressAnimation();
      }
    }
    handlePointercancel(event) {
      if (!this.shouldReactToEvent(event)) {
        return;
      }
      this.endPressAnimation();
    }
    handleContextmenu() {
      if (this.disabled) {
        return;
      }
      this.checkBoundsAfterContextMenu = true;
      this.endPressAnimation();
    }
    determineRippleSize() {
      const { height, width } = this.getBoundingClientRect();
      const maxDim = Math.max(height, width);
      const softEdgeSize = Math.max(SOFT_EDGE_CONTAINER_RATIO * maxDim, SOFT_EDGE_MINIMUM_SIZE);
      const initialSize = Math.floor(maxDim * INITIAL_ORIGIN_SCALE);
      const hypotenuse = Math.sqrt(width ** 2 + height ** 2);
      const maxRadius = hypotenuse + PADDING;
      this.initialSize = initialSize;
      this.rippleScale = `${(maxRadius + softEdgeSize) / initialSize}`;
      this.rippleSize = `${initialSize}px`;
    }
    getNormalizedPointerEventCoords(pointerEvent) {
      const { scrollX, scrollY } = window;
      const { left, top } = this.getBoundingClientRect();
      const documentX = scrollX + left;
      const documentY = scrollY + top;
      const { pageX, pageY } = pointerEvent;
      return { x: pageX - documentX, y: pageY - documentY };
    }
    getTranslationCoordinates(positionEvent) {
      const { height, width } = this.getBoundingClientRect();
      const endPoint = {
        x: (width - this.initialSize) / 2,
        y: (height - this.initialSize) / 2
      };
      let startPoint;
      if (positionEvent instanceof PointerEvent) {
        startPoint = this.getNormalizedPointerEventCoords(positionEvent);
      } else {
        startPoint = {
          x: width / 2,
          y: height / 2
        };
      }
      startPoint = {
        x: startPoint.x - this.initialSize / 2,
        y: startPoint.y - this.initialSize / 2
      };
      return { startPoint, endPoint };
    }
    startPressAnimation(positionEvent) {
      if (!this.mdRoot) {
        return;
      }
      this.pressed = true;
      this.growAnimation?.cancel();
      this.determineRippleSize();
      const { startPoint, endPoint } = this.getTranslationCoordinates(positionEvent);
      const translateStart = `${startPoint.x}px, ${startPoint.y}px`;
      const translateEnd = `${endPoint.x}px, ${endPoint.y}px`;
      this.growAnimation = this.mdRoot.animate({
        top: [0, 0],
        left: [0, 0],
        height: [this.rippleSize, this.rippleSize],
        width: [this.rippleSize, this.rippleSize],
        transform: [
          `translate(${translateStart}) scale(1)`,
          `translate(${translateEnd}) scale(${this.rippleScale})`
        ]
      }, {
        pseudoElement: PRESS_PSEUDO,
        duration: PRESS_GROW_MS,
        easing: EASING.STANDARD,
        fill: ANIMATION_FILL
      });
    }
    async endPressAnimation() {
      this.rippleStartEvent = void 0;
      this.state = State.INACTIVE;
      const animation = this.growAnimation;
      let pressAnimationPlayState = Infinity;
      if (typeof animation?.currentTime === "number") {
        pressAnimationPlayState = animation.currentTime;
      } else if (animation?.currentTime) {
        pressAnimationPlayState = animation.currentTime.to("ms").value;
      }
      if (pressAnimationPlayState >= MINIMUM_PRESS_MS) {
        this.pressed = false;
        return;
      }
      await new Promise((resolve) => {
        setTimeout(resolve, MINIMUM_PRESS_MS - pressAnimationPlayState);
      });
      if (this.growAnimation !== animation) {
        return;
      }
      this.pressed = false;
    }
    /**
     * Returns `true` if
     *  - the ripple element is enabled
     *  - the pointer is primary for the input type
     *  - the pointer is the pointer that started the interaction, or will start
     * the interaction
     *  - the pointer is a touch, or the pointer state has the primary button
     * held, or the pointer is hovering
     */
    shouldReactToEvent(event) {
      if (this.disabled || !event.isPrimary) {
        return false;
      }
      if (this.rippleStartEvent && this.rippleStartEvent.pointerId !== event.pointerId) {
        return false;
      }
      if (event.type === "pointerenter" || event.type === "pointerleave") {
        return !this.isTouch(event);
      }
      const isPrimaryButton = event.buttons === 1;
      return this.isTouch(event) || isPrimaryButton;
    }
    /**
     * Check if the event is within the bounds of the element.
     *
     * This is only needed for the "stuck" contextmenu longpress on Chrome.
     */
    inBounds({ x: x2, y: y2 }) {
      const { top, left, bottom, right } = this.getBoundingClientRect();
      return x2 >= left && x2 <= right && y2 >= top && y2 <= bottom;
    }
    isTouch({ pointerType }) {
      return pointerType === "touch";
    }
    /** @private */
    async handleEvent(event) {
      if (FORCED_COLORS?.matches) {
        return;
      }
      switch (event.type) {
        case "click":
          this.handleClick();
          break;
        case "contextmenu":
          this.handleContextmenu();
          break;
        case "pointercancel":
          this.handlePointercancel(event);
          break;
        case "pointerdown":
          await this.handlePointerdown(event);
          break;
        case "pointerenter":
          this.handlePointerenter(event);
          break;
        case "pointerleave":
          this.handlePointerleave(event);
          break;
        case "pointerup":
          this.handlePointerup(event);
          break;
        default:
          break;
      }
    }
    onControlChange(prev, next) {
      if (co)
        return;
      for (const event of EVENTS2) {
        prev?.removeEventListener(event, this);
        next?.addEventListener(event, this);
      }
    }
  };
  __decorate([
    n4({ type: Boolean, reflect: true })
  ], Ripple.prototype, "disabled", void 0);
  __decorate([
    r4()
  ], Ripple.prototype, "hovered", void 0);
  __decorate([
    r4()
  ], Ripple.prototype, "pressed", void 0);
  __decorate([
    e4(".surface")
  ], Ripple.prototype, "mdRoot", void 0);

  // node_modules/@material/web/ripple/internal/ripple-styles.js
  var styles3 = i`:host{display:flex;margin:auto;pointer-events:none}:host([disabled]){display:none}@media(forced-colors: active){:host{display:none}}:host,.surface{border-radius:inherit;position:absolute;inset:0;overflow:hidden}.surface{-webkit-tap-highlight-color:rgba(0,0,0,0)}.surface::before,.surface::after{content:"";opacity:0;position:absolute}.surface::before{background-color:var(--md-ripple-hover-color, var(--md-sys-color-on-surface, #1d1b20));inset:0;transition:opacity 15ms linear,background-color 15ms linear}.surface::after{background:radial-gradient(closest-side, var(--md-ripple-pressed-color, var(--md-sys-color-on-surface, #1d1b20)) max(100% - 70px, 65%), transparent 100%);transform-origin:center center;transition:opacity 375ms linear}.hovered::before{background-color:var(--md-ripple-hover-color, var(--md-sys-color-on-surface, #1d1b20));opacity:var(--md-ripple-hover-opacity, 0.08)}.pressed::after{opacity:var(--md-ripple-pressed-opacity, 0.12);transition-duration:105ms}
`;

  // node_modules/@material/web/ripple/ripple.js
  var MdRipple = class MdRipple2 extends Ripple {
  };
  MdRipple.styles = [styles3];
  MdRipple = __decorate([
    t2("md-ripple")
  ], MdRipple);

  // node_modules/@material/web/internal/aria/aria.js
  var ARIA_PROPERTIES = [
    "role",
    "ariaAtomic",
    "ariaAutoComplete",
    "ariaBusy",
    "ariaChecked",
    "ariaColCount",
    "ariaColIndex",
    "ariaColSpan",
    "ariaCurrent",
    "ariaDisabled",
    "ariaExpanded",
    "ariaHasPopup",
    "ariaHidden",
    "ariaInvalid",
    "ariaKeyShortcuts",
    "ariaLabel",
    "ariaLevel",
    "ariaLive",
    "ariaModal",
    "ariaMultiLine",
    "ariaMultiSelectable",
    "ariaOrientation",
    "ariaPlaceholder",
    "ariaPosInSet",
    "ariaPressed",
    "ariaReadOnly",
    "ariaRequired",
    "ariaRoleDescription",
    "ariaRowCount",
    "ariaRowIndex",
    "ariaRowSpan",
    "ariaSelected",
    "ariaSetSize",
    "ariaSort",
    "ariaValueMax",
    "ariaValueMin",
    "ariaValueNow",
    "ariaValueText"
  ];
  var ARIA_ATTRIBUTES = ARIA_PROPERTIES.map(ariaPropertyToAttribute);
  function isAriaAttribute(attribute) {
    return ARIA_ATTRIBUTES.includes(attribute);
  }
  function ariaPropertyToAttribute(property) {
    return property.replace("aria", "aria-").replace(/Elements?/g, "").toLowerCase();
  }

  // node_modules/@material/web/internal/aria/delegate.js
  var privateIgnoreAttributeChangesFor = Symbol("privateIgnoreAttributeChangesFor");
  function mixinDelegatesAria(base) {
    var _a2;
    if (co) {
      return base;
    }
    class WithDelegatesAriaElement extends base {
      constructor() {
        super(...arguments);
        this[_a2] = /* @__PURE__ */ new Set();
      }
      attributeChangedCallback(name, oldValue, newValue) {
        if (!isAriaAttribute(name)) {
          super.attributeChangedCallback(name, oldValue, newValue);
          return;
        }
        if (this[privateIgnoreAttributeChangesFor].has(name)) {
          return;
        }
        this[privateIgnoreAttributeChangesFor].add(name);
        this.removeAttribute(name);
        this[privateIgnoreAttributeChangesFor].delete(name);
        const dataProperty = ariaAttributeToDataProperty(name);
        if (newValue === null) {
          delete this.dataset[dataProperty];
        } else {
          this.dataset[dataProperty] = newValue;
        }
        this.requestUpdate(ariaAttributeToDataProperty(name), oldValue);
      }
      getAttribute(name) {
        if (isAriaAttribute(name)) {
          return super.getAttribute(ariaAttributeToDataAttribute(name));
        }
        return super.getAttribute(name);
      }
      removeAttribute(name) {
        super.removeAttribute(name);
        if (isAriaAttribute(name)) {
          super.removeAttribute(ariaAttributeToDataAttribute(name));
          this.requestUpdate();
        }
      }
    }
    _a2 = privateIgnoreAttributeChangesFor;
    setupDelegatesAriaProperties(WithDelegatesAriaElement);
    return WithDelegatesAriaElement;
  }
  function setupDelegatesAriaProperties(ctor) {
    for (const ariaProperty of ARIA_PROPERTIES) {
      const ariaAttribute = ariaPropertyToAttribute(ariaProperty);
      const dataAttribute = ariaAttributeToDataAttribute(ariaAttribute);
      const dataProperty = ariaAttributeToDataProperty(ariaAttribute);
      ctor.createProperty(ariaProperty, {
        attribute: ariaAttribute,
        noAccessor: true
      });
      ctor.createProperty(Symbol(dataAttribute), {
        attribute: dataAttribute,
        noAccessor: true
      });
      Object.defineProperty(ctor.prototype, ariaProperty, {
        configurable: true,
        enumerable: true,
        get() {
          return this.dataset[dataProperty] ?? null;
        },
        set(value) {
          const prevValue = this.dataset[dataProperty] ?? null;
          if (value === prevValue) {
            return;
          }
          if (value === null) {
            delete this.dataset[dataProperty];
          } else {
            this.dataset[dataProperty] = value;
          }
          this.requestUpdate(ariaProperty, prevValue);
        }
      });
    }
  }
  function ariaAttributeToDataAttribute(ariaAttribute) {
    return `data-${ariaAttribute}`;
  }
  function ariaAttributeToDataProperty(ariaAttribute) {
    return ariaAttribute.replace(/-\w/, (dashLetter) => dashLetter[1].toUpperCase());
  }

  // node_modules/@material/web/labs/behaviors/element-internals.js
  var internals = Symbol("internals");
  var privateInternals = Symbol("privateInternals");
  function mixinElementInternals(base) {
    class WithElementInternalsElement extends base {
      get [internals]() {
        if (!this[privateInternals]) {
          this[privateInternals] = this.attachInternals();
        }
        return this[privateInternals];
      }
    }
    return WithElementInternalsElement;
  }

  // node_modules/@material/web/internal/controller/form-submitter.js
  function setupFormSubmitter(ctor) {
    if (co) {
      return;
    }
    ctor.addInitializer((instance) => {
      const submitter = instance;
      submitter.addEventListener("click", async (event) => {
        const { type, [internals]: elementInternals } = submitter;
        const { form } = elementInternals;
        if (!form || type === "button") {
          return;
        }
        await new Promise((resolve) => {
          setTimeout(resolve);
        });
        if (event.defaultPrevented) {
          return;
        }
        if (type === "reset") {
          form.reset();
          return;
        }
        form.addEventListener("submit", (submitEvent) => {
          Object.defineProperty(submitEvent, "submitter", {
            configurable: true,
            enumerable: true,
            get: () => submitter
          });
        }, { capture: true, once: true });
        elementInternals.setFormValue(submitter.value);
        form.requestSubmit();
      });
    });
  }

  // node_modules/@material/web/internal/events/form-label-activation.js
  function dispatchActivationClick(element) {
    const event = new MouseEvent("click", { bubbles: true });
    element.dispatchEvent(event);
    return event;
  }
  function isActivationClick(event) {
    if (event.currentTarget !== event.target) {
      return false;
    }
    if (event.composedPath()[0] !== event.target) {
      return false;
    }
    if (event.target.disabled) {
      return false;
    }
    return !squelchEvent(event);
  }
  function squelchEvent(event) {
    const squelched = isSquelchingEvents;
    if (squelched) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
    squelchEventsForMicrotask();
    return squelched;
  }
  var isSquelchingEvents = false;
  async function squelchEventsForMicrotask() {
    isSquelchingEvents = true;
    await null;
    isSquelchingEvents = false;
  }

  // node_modules/@material/web/button/internal/button.js
  var buttonBaseClass = mixinDelegatesAria(mixinElementInternals(h3));
  var Button = class extends buttonBaseClass {
    get name() {
      return this.getAttribute("name") ?? "";
    }
    set name(name) {
      this.setAttribute("name", name);
    }
    /**
     * The associated form element with which this element's value will submit.
     */
    get form() {
      return this[internals].form;
    }
    constructor() {
      super();
      this.disabled = false;
      this.softDisabled = false;
      this.href = "";
      this.target = "";
      this.trailingIcon = false;
      this.hasIcon = false;
      this.type = "submit";
      this.value = "";
      if (!co) {
        this.addEventListener("click", this.handleClick.bind(this));
      }
    }
    focus() {
      this.buttonElement?.focus();
    }
    blur() {
      this.buttonElement?.blur();
    }
    render() {
      const isRippleDisabled = !this.href && (this.disabled || this.softDisabled);
      const buttonOrLink = this.href ? this.renderLink() : this.renderButton();
      const buttonId = this.href ? "link" : "button";
      return ke`
      ${this.renderElevationOrOutline?.()}
      <div class="background"></div>
      <md-focus-ring part="focus-ring" for=${buttonId}></md-focus-ring>
      <md-ripple
        part="ripple"
        for=${buttonId}
        ?disabled="${isRippleDisabled}"></md-ripple>
      ${buttonOrLink}
    `;
    }
    renderButton() {
      const { ariaLabel, ariaHasPopup, ariaExpanded } = this;
      return ke`<button
      id="button"
      class="button"
      ?disabled=${this.disabled}
      aria-disabled=${this.softDisabled || D}
      aria-label="${ariaLabel || D}"
      aria-haspopup="${ariaHasPopup || D}"
      aria-expanded="${ariaExpanded || D}">
      ${this.renderContent()}
    </button>`;
    }
    renderLink() {
      const { ariaLabel, ariaHasPopup, ariaExpanded } = this;
      return ke`<a
      id="link"
      class="button"
      aria-label="${ariaLabel || D}"
      aria-haspopup="${ariaHasPopup || D}"
      aria-expanded="${ariaExpanded || D}"
      href=${this.href}
      target=${this.target || D}
      >${this.renderContent()}
    </a>`;
    }
    renderContent() {
      const icon = ke`<slot
      name="icon"
      @slotchange="${this.handleSlotChange}"></slot>`;
      return ke`
      <span class="touch"></span>
      ${this.trailingIcon ? D : icon}
      <span class="label"><slot></slot></span>
      ${this.trailingIcon ? icon : D}
    `;
    }
    handleClick(event) {
      if (!this.href && this.softDisabled) {
        event.stopImmediatePropagation();
        event.preventDefault();
        return;
      }
      if (!isActivationClick(event) || !this.buttonElement) {
        return;
      }
      this.focus();
      dispatchActivationClick(this.buttonElement);
    }
    handleSlotChange() {
      this.hasIcon = this.assignedIcons.length > 0;
    }
  };
  (() => {
    setupFormSubmitter(Button);
  })();
  Button.formAssociated = true;
  Button.shadowRootOptions = {
    mode: "open",
    delegatesFocus: true
  };
  __decorate([
    n4({ type: Boolean, reflect: true })
  ], Button.prototype, "disabled", void 0);
  __decorate([
    n4({ type: Boolean, attribute: "soft-disabled", reflect: true })
  ], Button.prototype, "softDisabled", void 0);
  __decorate([
    n4()
  ], Button.prototype, "href", void 0);
  __decorate([
    n4()
  ], Button.prototype, "target", void 0);
  __decorate([
    n4({ type: Boolean, attribute: "trailing-icon", reflect: true })
  ], Button.prototype, "trailingIcon", void 0);
  __decorate([
    n4({ type: Boolean, attribute: "has-icon", reflect: true })
  ], Button.prototype, "hasIcon", void 0);
  __decorate([
    n4()
  ], Button.prototype, "type", void 0);
  __decorate([
    n4({ reflect: true })
  ], Button.prototype, "value", void 0);
  __decorate([
    e4(".button")
  ], Button.prototype, "buttonElement", void 0);
  __decorate([
    o4({ slot: "icon", flatten: true })
  ], Button.prototype, "assignedIcons", void 0);

  // node_modules/@material/web/button/internal/filled-button.js
  var FilledButton = class extends Button {
    renderElevationOrOutline() {
      return ke`<md-elevation part="elevation"></md-elevation>`;
    }
  };

  // node_modules/@material/web/button/internal/filled-styles.js
  var styles4 = i`:host{--_container-color: var(--md-filled-button-container-color, var(--md-sys-color-primary, #6750a4));--_container-elevation: var(--md-filled-button-container-elevation, 0);--_container-height: var(--md-filled-button-container-height, 40px);--_container-shadow-color: var(--md-filled-button-container-shadow-color, var(--md-sys-color-shadow, #000));--_disabled-container-color: var(--md-filled-button-disabled-container-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-container-elevation: var(--md-filled-button-disabled-container-elevation, 0);--_disabled-container-opacity: var(--md-filled-button-disabled-container-opacity, 0.12);--_disabled-label-text-color: var(--md-filled-button-disabled-label-text-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-label-text-opacity: var(--md-filled-button-disabled-label-text-opacity, 0.38);--_focus-container-elevation: var(--md-filled-button-focus-container-elevation, 0);--_focus-label-text-color: var(--md-filled-button-focus-label-text-color, var(--md-sys-color-on-primary, #fff));--_hover-container-elevation: var(--md-filled-button-hover-container-elevation, 1);--_hover-label-text-color: var(--md-filled-button-hover-label-text-color, var(--md-sys-color-on-primary, #fff));--_hover-state-layer-color: var(--md-filled-button-hover-state-layer-color, var(--md-sys-color-on-primary, #fff));--_hover-state-layer-opacity: var(--md-filled-button-hover-state-layer-opacity, 0.08);--_label-text-color: var(--md-filled-button-label-text-color, var(--md-sys-color-on-primary, #fff));--_label-text-font: var(--md-filled-button-label-text-font, var(--md-sys-typescale-label-large-font, var(--md-ref-typeface-plain, Roboto)));--_label-text-line-height: var(--md-filled-button-label-text-line-height, var(--md-sys-typescale-label-large-line-height, 1.25rem));--_label-text-size: var(--md-filled-button-label-text-size, var(--md-sys-typescale-label-large-size, 0.875rem));--_label-text-weight: var(--md-filled-button-label-text-weight, var(--md-sys-typescale-label-large-weight, var(--md-ref-typeface-weight-medium, 500)));--_pressed-container-elevation: var(--md-filled-button-pressed-container-elevation, 0);--_pressed-label-text-color: var(--md-filled-button-pressed-label-text-color, var(--md-sys-color-on-primary, #fff));--_pressed-state-layer-color: var(--md-filled-button-pressed-state-layer-color, var(--md-sys-color-on-primary, #fff));--_pressed-state-layer-opacity: var(--md-filled-button-pressed-state-layer-opacity, 0.12);--_disabled-icon-color: var(--md-filled-button-disabled-icon-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-icon-opacity: var(--md-filled-button-disabled-icon-opacity, 0.38);--_focus-icon-color: var(--md-filled-button-focus-icon-color, var(--md-sys-color-on-primary, #fff));--_hover-icon-color: var(--md-filled-button-hover-icon-color, var(--md-sys-color-on-primary, #fff));--_icon-color: var(--md-filled-button-icon-color, var(--md-sys-color-on-primary, #fff));--_icon-size: var(--md-filled-button-icon-size, 18px);--_pressed-icon-color: var(--md-filled-button-pressed-icon-color, var(--md-sys-color-on-primary, #fff));--_container-shape-start-start: var(--md-filled-button-container-shape-start-start, var(--md-filled-button-container-shape, var(--md-sys-shape-corner-full, 9999px)));--_container-shape-start-end: var(--md-filled-button-container-shape-start-end, var(--md-filled-button-container-shape, var(--md-sys-shape-corner-full, 9999px)));--_container-shape-end-end: var(--md-filled-button-container-shape-end-end, var(--md-filled-button-container-shape, var(--md-sys-shape-corner-full, 9999px)));--_container-shape-end-start: var(--md-filled-button-container-shape-end-start, var(--md-filled-button-container-shape, var(--md-sys-shape-corner-full, 9999px)));--_leading-space: var(--md-filled-button-leading-space, 24px);--_trailing-space: var(--md-filled-button-trailing-space, 24px);--_with-leading-icon-leading-space: var(--md-filled-button-with-leading-icon-leading-space, 16px);--_with-leading-icon-trailing-space: var(--md-filled-button-with-leading-icon-trailing-space, 24px);--_with-trailing-icon-leading-space: var(--md-filled-button-with-trailing-icon-leading-space, 24px);--_with-trailing-icon-trailing-space: var(--md-filled-button-with-trailing-icon-trailing-space, 16px)}
`;

  // node_modules/@material/web/button/internal/shared-elevation-styles.js
  var styles5 = i`md-elevation{transition-duration:280ms}:host(:is([disabled],[soft-disabled])) md-elevation{transition:none}md-elevation{--md-elevation-level: var(--_container-elevation);--md-elevation-shadow-color: var(--_container-shadow-color)}:host(:focus-within) md-elevation{--md-elevation-level: var(--_focus-container-elevation)}:host(:hover) md-elevation{--md-elevation-level: var(--_hover-container-elevation)}:host(:active) md-elevation{--md-elevation-level: var(--_pressed-container-elevation)}:host(:is([disabled],[soft-disabled])) md-elevation{--md-elevation-level: var(--_disabled-container-elevation)}
`;

  // node_modules/@material/web/button/internal/shared-styles.js
  var styles6 = i`:host{border-start-start-radius:var(--_container-shape-start-start);border-start-end-radius:var(--_container-shape-start-end);border-end-start-radius:var(--_container-shape-end-start);border-end-end-radius:var(--_container-shape-end-end);box-sizing:border-box;cursor:pointer;display:inline-flex;gap:8px;min-height:var(--_container-height);outline:none;padding-block:calc((var(--_container-height) - max(var(--_label-text-line-height),var(--_icon-size)))/2);padding-inline-start:var(--_leading-space);padding-inline-end:var(--_trailing-space);place-content:center;place-items:center;position:relative;font-family:var(--_label-text-font);font-size:var(--_label-text-size);line-height:var(--_label-text-line-height);font-weight:var(--_label-text-weight);text-overflow:ellipsis;text-wrap:nowrap;user-select:none;-webkit-tap-highlight-color:rgba(0,0,0,0);vertical-align:top;--md-ripple-hover-color: var(--_hover-state-layer-color);--md-ripple-pressed-color: var(--_pressed-state-layer-color);--md-ripple-hover-opacity: var(--_hover-state-layer-opacity);--md-ripple-pressed-opacity: var(--_pressed-state-layer-opacity)}md-focus-ring{--md-focus-ring-shape-start-start: var(--_container-shape-start-start);--md-focus-ring-shape-start-end: var(--_container-shape-start-end);--md-focus-ring-shape-end-end: var(--_container-shape-end-end);--md-focus-ring-shape-end-start: var(--_container-shape-end-start)}:host(:is([disabled],[soft-disabled])){cursor:default;pointer-events:none}.button{border-radius:inherit;cursor:inherit;display:inline-flex;align-items:center;justify-content:center;border:none;outline:none;-webkit-appearance:none;vertical-align:middle;background:rgba(0,0,0,0);text-decoration:none;min-width:calc(64px - var(--_leading-space) - var(--_trailing-space));width:100%;z-index:0;height:100%;font:inherit;color:var(--_label-text-color);padding:0;gap:inherit;text-transform:inherit}.button::-moz-focus-inner{padding:0;border:0}:host(:hover) .button{color:var(--_hover-label-text-color)}:host(:focus-within) .button{color:var(--_focus-label-text-color)}:host(:active) .button{color:var(--_pressed-label-text-color)}.background{background-color:var(--_container-color);border-radius:inherit;inset:0;position:absolute}.label{overflow:hidden}:is(.button,.label,.label slot),.label ::slotted(*){text-overflow:inherit}:host(:is([disabled],[soft-disabled])) .label{color:var(--_disabled-label-text-color);opacity:var(--_disabled-label-text-opacity)}:host(:is([disabled],[soft-disabled])) .background{background-color:var(--_disabled-container-color);opacity:var(--_disabled-container-opacity)}@media(forced-colors: active){.background{border:1px solid CanvasText}:host(:is([disabled],[soft-disabled])){--_disabled-icon-color: GrayText;--_disabled-icon-opacity: 1;--_disabled-container-opacity: 1;--_disabled-label-text-color: GrayText;--_disabled-label-text-opacity: 1}}:host([has-icon]:not([trailing-icon])){padding-inline-start:var(--_with-leading-icon-leading-space);padding-inline-end:var(--_with-leading-icon-trailing-space)}:host([has-icon][trailing-icon]){padding-inline-start:var(--_with-trailing-icon-leading-space);padding-inline-end:var(--_with-trailing-icon-trailing-space)}::slotted([slot=icon]){display:inline-flex;position:relative;writing-mode:horizontal-tb;fill:currentColor;flex-shrink:0;color:var(--_icon-color);font-size:var(--_icon-size);inline-size:var(--_icon-size);block-size:var(--_icon-size)}:host(:hover) ::slotted([slot=icon]){color:var(--_hover-icon-color)}:host(:focus-within) ::slotted([slot=icon]){color:var(--_focus-icon-color)}:host(:active) ::slotted([slot=icon]){color:var(--_pressed-icon-color)}:host(:is([disabled],[soft-disabled])) ::slotted([slot=icon]){color:var(--_disabled-icon-color);opacity:var(--_disabled-icon-opacity)}.touch{position:absolute;top:50%;height:48px;left:0;right:0;transform:translateY(-50%)}:host([touch-target=wrapper]){margin:max(0px,(48px - var(--_container-height))/2) 0}:host([touch-target=none]) .touch{display:none}
`;

  // node_modules/@material/web/button/filled-button.js
  var MdFilledButton = class MdFilledButton2 extends FilledButton {
  };
  MdFilledButton.styles = [
    styles6,
    styles5,
    styles4
  ];
  MdFilledButton = __decorate([
    t2("md-filled-button")
  ], MdFilledButton);

  // node_modules/@material/web/button/internal/filled-tonal-button.js
  var FilledTonalButton = class extends Button {
    renderElevationOrOutline() {
      return ke`<md-elevation part="elevation"></md-elevation>`;
    }
  };

  // node_modules/@material/web/button/internal/filled-tonal-styles.js
  var styles7 = i`:host{--_container-color: var(--md-filled-tonal-button-container-color, var(--md-sys-color-secondary-container, #e8def8));--_container-elevation: var(--md-filled-tonal-button-container-elevation, 0);--_container-height: var(--md-filled-tonal-button-container-height, 40px);--_container-shadow-color: var(--md-filled-tonal-button-container-shadow-color, var(--md-sys-color-shadow, #000));--_disabled-container-color: var(--md-filled-tonal-button-disabled-container-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-container-elevation: var(--md-filled-tonal-button-disabled-container-elevation, 0);--_disabled-container-opacity: var(--md-filled-tonal-button-disabled-container-opacity, 0.12);--_disabled-label-text-color: var(--md-filled-tonal-button-disabled-label-text-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-label-text-opacity: var(--md-filled-tonal-button-disabled-label-text-opacity, 0.38);--_focus-container-elevation: var(--md-filled-tonal-button-focus-container-elevation, 0);--_focus-label-text-color: var(--md-filled-tonal-button-focus-label-text-color, var(--md-sys-color-on-secondary-container, #1d192b));--_hover-container-elevation: var(--md-filled-tonal-button-hover-container-elevation, 1);--_hover-label-text-color: var(--md-filled-tonal-button-hover-label-text-color, var(--md-sys-color-on-secondary-container, #1d192b));--_hover-state-layer-color: var(--md-filled-tonal-button-hover-state-layer-color, var(--md-sys-color-on-secondary-container, #1d192b));--_hover-state-layer-opacity: var(--md-filled-tonal-button-hover-state-layer-opacity, 0.08);--_label-text-color: var(--md-filled-tonal-button-label-text-color, var(--md-sys-color-on-secondary-container, #1d192b));--_label-text-font: var(--md-filled-tonal-button-label-text-font, var(--md-sys-typescale-label-large-font, var(--md-ref-typeface-plain, Roboto)));--_label-text-line-height: var(--md-filled-tonal-button-label-text-line-height, var(--md-sys-typescale-label-large-line-height, 1.25rem));--_label-text-size: var(--md-filled-tonal-button-label-text-size, var(--md-sys-typescale-label-large-size, 0.875rem));--_label-text-weight: var(--md-filled-tonal-button-label-text-weight, var(--md-sys-typescale-label-large-weight, var(--md-ref-typeface-weight-medium, 500)));--_pressed-container-elevation: var(--md-filled-tonal-button-pressed-container-elevation, 0);--_pressed-label-text-color: var(--md-filled-tonal-button-pressed-label-text-color, var(--md-sys-color-on-secondary-container, #1d192b));--_pressed-state-layer-color: var(--md-filled-tonal-button-pressed-state-layer-color, var(--md-sys-color-on-secondary-container, #1d192b));--_pressed-state-layer-opacity: var(--md-filled-tonal-button-pressed-state-layer-opacity, 0.12);--_disabled-icon-color: var(--md-filled-tonal-button-disabled-icon-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-icon-opacity: var(--md-filled-tonal-button-disabled-icon-opacity, 0.38);--_focus-icon-color: var(--md-filled-tonal-button-focus-icon-color, var(--md-sys-color-on-secondary-container, #1d192b));--_hover-icon-color: var(--md-filled-tonal-button-hover-icon-color, var(--md-sys-color-on-secondary-container, #1d192b));--_icon-color: var(--md-filled-tonal-button-icon-color, var(--md-sys-color-on-secondary-container, #1d192b));--_icon-size: var(--md-filled-tonal-button-icon-size, 18px);--_pressed-icon-color: var(--md-filled-tonal-button-pressed-icon-color, var(--md-sys-color-on-secondary-container, #1d192b));--_container-shape-start-start: var(--md-filled-tonal-button-container-shape-start-start, var(--md-filled-tonal-button-container-shape, var(--md-sys-shape-corner-full, 9999px)));--_container-shape-start-end: var(--md-filled-tonal-button-container-shape-start-end, var(--md-filled-tonal-button-container-shape, var(--md-sys-shape-corner-full, 9999px)));--_container-shape-end-end: var(--md-filled-tonal-button-container-shape-end-end, var(--md-filled-tonal-button-container-shape, var(--md-sys-shape-corner-full, 9999px)));--_container-shape-end-start: var(--md-filled-tonal-button-container-shape-end-start, var(--md-filled-tonal-button-container-shape, var(--md-sys-shape-corner-full, 9999px)));--_leading-space: var(--md-filled-tonal-button-leading-space, 24px);--_trailing-space: var(--md-filled-tonal-button-trailing-space, 24px);--_with-leading-icon-leading-space: var(--md-filled-tonal-button-with-leading-icon-leading-space, 16px);--_with-leading-icon-trailing-space: var(--md-filled-tonal-button-with-leading-icon-trailing-space, 24px);--_with-trailing-icon-leading-space: var(--md-filled-tonal-button-with-trailing-icon-leading-space, 24px);--_with-trailing-icon-trailing-space: var(--md-filled-tonal-button-with-trailing-icon-trailing-space, 16px)}
`;

  // node_modules/@material/web/button/filled-tonal-button.js
  var MdFilledTonalButton = class MdFilledTonalButton2 extends FilledTonalButton {
  };
  MdFilledTonalButton.styles = [
    styles6,
    styles5,
    styles7
  ];
  MdFilledTonalButton = __decorate([
    t2("md-filled-tonal-button")
  ], MdFilledTonalButton);

  // node_modules/@material/web/button/internal/text-button.js
  var TextButton = class extends Button {
  };

  // node_modules/@material/web/button/internal/text-styles.js
  var styles8 = i`:host{--_container-height: var(--md-text-button-container-height, 40px);--_disabled-label-text-color: var(--md-text-button-disabled-label-text-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-label-text-opacity: var(--md-text-button-disabled-label-text-opacity, 0.38);--_focus-label-text-color: var(--md-text-button-focus-label-text-color, var(--md-sys-color-primary, #6750a4));--_hover-label-text-color: var(--md-text-button-hover-label-text-color, var(--md-sys-color-primary, #6750a4));--_hover-state-layer-color: var(--md-text-button-hover-state-layer-color, var(--md-sys-color-primary, #6750a4));--_hover-state-layer-opacity: var(--md-text-button-hover-state-layer-opacity, 0.08);--_label-text-color: var(--md-text-button-label-text-color, var(--md-sys-color-primary, #6750a4));--_label-text-font: var(--md-text-button-label-text-font, var(--md-sys-typescale-label-large-font, var(--md-ref-typeface-plain, Roboto)));--_label-text-line-height: var(--md-text-button-label-text-line-height, var(--md-sys-typescale-label-large-line-height, 1.25rem));--_label-text-size: var(--md-text-button-label-text-size, var(--md-sys-typescale-label-large-size, 0.875rem));--_label-text-weight: var(--md-text-button-label-text-weight, var(--md-sys-typescale-label-large-weight, var(--md-ref-typeface-weight-medium, 500)));--_pressed-label-text-color: var(--md-text-button-pressed-label-text-color, var(--md-sys-color-primary, #6750a4));--_pressed-state-layer-color: var(--md-text-button-pressed-state-layer-color, var(--md-sys-color-primary, #6750a4));--_pressed-state-layer-opacity: var(--md-text-button-pressed-state-layer-opacity, 0.12);--_disabled-icon-color: var(--md-text-button-disabled-icon-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-icon-opacity: var(--md-text-button-disabled-icon-opacity, 0.38);--_focus-icon-color: var(--md-text-button-focus-icon-color, var(--md-sys-color-primary, #6750a4));--_hover-icon-color: var(--md-text-button-hover-icon-color, var(--md-sys-color-primary, #6750a4));--_icon-color: var(--md-text-button-icon-color, var(--md-sys-color-primary, #6750a4));--_icon-size: var(--md-text-button-icon-size, 18px);--_pressed-icon-color: var(--md-text-button-pressed-icon-color, var(--md-sys-color-primary, #6750a4));--_container-shape-start-start: var(--md-text-button-container-shape-start-start, var(--md-text-button-container-shape, var(--md-sys-shape-corner-full, 9999px)));--_container-shape-start-end: var(--md-text-button-container-shape-start-end, var(--md-text-button-container-shape, var(--md-sys-shape-corner-full, 9999px)));--_container-shape-end-end: var(--md-text-button-container-shape-end-end, var(--md-text-button-container-shape, var(--md-sys-shape-corner-full, 9999px)));--_container-shape-end-start: var(--md-text-button-container-shape-end-start, var(--md-text-button-container-shape, var(--md-sys-shape-corner-full, 9999px)));--_leading-space: var(--md-text-button-leading-space, 12px);--_trailing-space: var(--md-text-button-trailing-space, 12px);--_with-leading-icon-leading-space: var(--md-text-button-with-leading-icon-leading-space, 12px);--_with-leading-icon-trailing-space: var(--md-text-button-with-leading-icon-trailing-space, 16px);--_with-trailing-icon-leading-space: var(--md-text-button-with-trailing-icon-leading-space, 16px);--_with-trailing-icon-trailing-space: var(--md-text-button-with-trailing-icon-trailing-space, 12px);--_container-color: none;--_disabled-container-color: none;--_disabled-container-opacity: 0}
`;

  // node_modules/@material/web/button/text-button.js
  var MdTextButton = class MdTextButton2 extends TextButton {
  };
  MdTextButton.styles = [styles6, styles8];
  MdTextButton = __decorate([
    t2("md-text-button")
  ], MdTextButton);

  // node_modules/@material/web/divider/internal/divider.js
  var Divider = class extends h3 {
    constructor() {
      super(...arguments);
      this.inset = false;
      this.insetStart = false;
      this.insetEnd = false;
    }
  };
  __decorate([
    n4({ type: Boolean, reflect: true })
  ], Divider.prototype, "inset", void 0);
  __decorate([
    n4({ type: Boolean, reflect: true, attribute: "inset-start" })
  ], Divider.prototype, "insetStart", void 0);
  __decorate([
    n4({ type: Boolean, reflect: true, attribute: "inset-end" })
  ], Divider.prototype, "insetEnd", void 0);

  // node_modules/@material/web/divider/internal/divider-styles.js
  var styles9 = i`:host{box-sizing:border-box;color:var(--md-divider-color, var(--md-sys-color-outline-variant, #cac4d0));display:flex;height:var(--md-divider-thickness, 1px);width:100%}:host([inset]),:host([inset-start]){padding-inline-start:16px}:host([inset]),:host([inset-end]){padding-inline-end:16px}:host::before{background:currentColor;content:"";height:100%;width:100%}@media(forced-colors: active){:host::before{background:CanvasText}}
`;

  // node_modules/@material/web/divider/divider.js
  var MdDivider = class MdDivider2 extends Divider {
  };
  MdDivider.styles = [styles9];
  MdDivider = __decorate([
    t2("md-divider")
  ], MdDivider);

  // node_modules/@material/web/internal/events/redispatch-event.js
  function redispatchEvent(element, event) {
    if (event.bubbles && (!element.shadowRoot || event.composed)) {
      event.stopPropagation();
    }
    const copy = Reflect.construct(event.constructor, [event.type, event]);
    const dispatched = element.dispatchEvent(copy);
    if (!dispatched) {
      event.preventDefault();
    }
    return dispatched;
  }

  // node_modules/@material/web/dialog/internal/animations.js
  var DIALOG_DEFAULT_OPEN_ANIMATION = {
    dialog: [
      [
        // Dialog slide down
        [{ "transform": "translateY(-50px)" }, { "transform": "translateY(0)" }],
        { duration: 500, easing: EASING.EMPHASIZED }
      ]
    ],
    scrim: [
      [
        // Scrim fade in
        [{ "opacity": 0 }, { "opacity": 0.32 }],
        { duration: 500, easing: "linear" }
      ]
    ],
    container: [
      [
        // Container fade in
        [{ "opacity": 0 }, { "opacity": 1 }],
        { duration: 50, easing: "linear", pseudoElement: "::before" }
      ],
      [
        // Container grow
        // Note: current spec says to grow from 0dp->100% and shrink from
        // 100%->35%. We change this to 35%->100% to simplify the animation that
        // is supposed to clip content as it grows. From 0dp it's possible to see
        // text/actions appear before the container has fully grown.
        [{ "height": "35%" }, { "height": "100%" }],
        { duration: 500, easing: EASING.EMPHASIZED, pseudoElement: "::before" }
      ]
    ],
    headline: [
      [
        // Headline fade in
        [{ "opacity": 0 }, { "opacity": 0, offset: 0.2 }, { "opacity": 1 }],
        { duration: 250, easing: "linear", fill: "forwards" }
      ]
    ],
    content: [
      [
        // Content fade in
        [{ "opacity": 0 }, { "opacity": 0, offset: 0.2 }, { "opacity": 1 }],
        { duration: 250, easing: "linear", fill: "forwards" }
      ]
    ],
    actions: [
      [
        // Actions fade in
        [{ "opacity": 0 }, { "opacity": 0, offset: 0.5 }, { "opacity": 1 }],
        { duration: 300, easing: "linear", fill: "forwards" }
      ]
    ]
  };
  var DIALOG_DEFAULT_CLOSE_ANIMATION = {
    dialog: [
      [
        // Dialog slide up
        [{ "transform": "translateY(0)" }, { "transform": "translateY(-50px)" }],
        { duration: 150, easing: EASING.EMPHASIZED_ACCELERATE }
      ]
    ],
    scrim: [
      [
        // Scrim fade out
        [{ "opacity": 0.32 }, { "opacity": 0 }],
        { duration: 150, easing: "linear" }
      ]
    ],
    container: [
      [
        // Container shrink
        [{ "height": "100%" }, { "height": "35%" }],
        {
          duration: 150,
          easing: EASING.EMPHASIZED_ACCELERATE,
          pseudoElement: "::before"
        }
      ],
      [
        // Container fade out
        [{ "opacity": "1" }, { "opacity": "0" }],
        { delay: 100, duration: 50, easing: "linear", pseudoElement: "::before" }
      ]
    ],
    headline: [
      [
        // Headline fade out
        [{ "opacity": 1 }, { "opacity": 0 }],
        { duration: 100, easing: "linear", fill: "forwards" }
      ]
    ],
    content: [
      [
        // Content fade out
        [{ "opacity": 1 }, { "opacity": 0 }],
        { duration: 100, easing: "linear", fill: "forwards" }
      ]
    ],
    actions: [
      [
        // Actions fade out
        [{ "opacity": 1 }, { "opacity": 0 }],
        { duration: 100, easing: "linear", fill: "forwards" }
      ]
    ]
  };

  // node_modules/@material/web/dialog/internal/dialog.js
  var dialogBaseClass = mixinDelegatesAria(h3);
  var Dialog = class extends dialogBaseClass {
    // We do not use `delegatesFocus: true` due to a Chromium bug with
    // selecting text.
    // See https://bugs.chromium.org/p/chromium/issues/detail?id=950357
    /**
     * Opens the dialog when set to `true` and closes it when set to `false`.
     */
    get open() {
      return this.isOpen;
    }
    set open(open) {
      if (open === this.isOpen) {
        return;
      }
      this.isOpen = open;
      if (open) {
        this.setAttribute("open", "");
        this.show();
      } else {
        this.removeAttribute("open");
        this.close();
      }
    }
    constructor() {
      super();
      this.quick = false;
      this.returnValue = "";
      this.noFocusTrap = false;
      this.getOpenAnimation = () => DIALOG_DEFAULT_OPEN_ANIMATION;
      this.getCloseAnimation = () => DIALOG_DEFAULT_CLOSE_ANIMATION;
      this.isOpen = false;
      this.isOpening = false;
      this.isConnectedPromise = this.getIsConnectedPromise();
      this.isAtScrollTop = false;
      this.isAtScrollBottom = false;
      this.nextClickIsFromContent = false;
      this.hasHeadline = false;
      this.hasActions = false;
      this.hasIcon = false;
      this.escapePressedWithoutCancel = false;
      this.treewalker = co ? null : document.createTreeWalker(this, NodeFilter.SHOW_ELEMENT);
      if (!co) {
        this.addEventListener("submit", this.handleSubmit);
      }
    }
    /**
     * Opens the dialog and fires a cancelable `open` event. After a dialog's
     * animation, an `opened` event is fired.
     *
     * Add an `autofocus` attribute to a child of the dialog that should
     * receive focus after opening.
     *
     * @return A Promise that resolves after the animation is finished and the
     *     `opened` event was fired.
     */
    async show() {
      this.isOpening = true;
      await this.isConnectedPromise;
      await this.updateComplete;
      const dialog = this.dialog;
      if (dialog.open || !this.isOpening) {
        this.isOpening = false;
        return;
      }
      const preventOpen = !this.dispatchEvent(new Event("open", { cancelable: true }));
      if (preventOpen) {
        this.open = false;
        this.isOpening = false;
        return;
      }
      dialog.showModal();
      this.open = true;
      if (this.scroller) {
        this.scroller.scrollTop = 0;
      }
      this.querySelector("[autofocus]")?.focus();
      await this.animateDialog(this.getOpenAnimation());
      this.dispatchEvent(new Event("opened"));
      this.isOpening = false;
    }
    /**
     * Closes the dialog and fires a cancelable `close` event. After a dialog's
     * animation, a `closed` event is fired.
     *
     * @param returnValue A return value usually indicating which button was used
     *     to close a dialog. If a dialog is canceled by clicking the scrim or
     *     pressing Escape, it will not change the return value after closing.
     * @return A Promise that resolves after the animation is finished and the
     *     `closed` event was fired.
     */
    async close(returnValue = this.returnValue) {
      this.isOpening = false;
      if (!this.isConnected) {
        this.open = false;
        return;
      }
      await this.updateComplete;
      const dialog = this.dialog;
      if (!dialog.open || this.isOpening) {
        this.open = false;
        return;
      }
      const prevReturnValue = this.returnValue;
      this.returnValue = returnValue;
      const preventClose = !this.dispatchEvent(new Event("close", { cancelable: true }));
      if (preventClose) {
        this.returnValue = prevReturnValue;
        return;
      }
      await this.animateDialog(this.getCloseAnimation());
      dialog.close(returnValue);
      this.open = false;
      this.dispatchEvent(new Event("closed"));
    }
    connectedCallback() {
      super.connectedCallback();
      this.isConnectedPromiseResolve();
    }
    disconnectedCallback() {
      super.disconnectedCallback();
      this.isConnectedPromise = this.getIsConnectedPromise();
    }
    render() {
      const scrollable = this.open && !(this.isAtScrollTop && this.isAtScrollBottom);
      const classes = {
        "has-headline": this.hasHeadline,
        "has-actions": this.hasActions,
        "has-icon": this.hasIcon,
        "scrollable": scrollable,
        "show-top-divider": scrollable && !this.isAtScrollTop,
        "show-bottom-divider": scrollable && !this.isAtScrollBottom
      };
      const showFocusTrap = this.open && !this.noFocusTrap;
      const focusTrap = ke`
      <div
        class="focus-trap"
        tabindex="0"
        aria-hidden="true"
        @focus=${this.handleFocusTrapFocus}></div>
    `;
      const { ariaLabel } = this;
      return ke`
      <div class="scrim"></div>
      <dialog
        class=${Rt(classes)}
        aria-label=${ariaLabel || D}
        aria-labelledby=${this.hasHeadline ? "headline" : D}
        role=${this.type === "alert" ? "alertdialog" : D}
        @cancel=${this.handleCancel}
        @click=${this.handleDialogClick}
        @close=${this.handleClose}
        @keydown=${this.handleKeydown}
        .returnValue=${this.returnValue || D}>
        ${showFocusTrap ? focusTrap : D}
        <div class="container" @click=${this.handleContentClick}>
          <div class="headline">
            <div class="icon" aria-hidden="true">
              <slot name="icon" @slotchange=${this.handleIconChange}></slot>
            </div>
            <h2 id="headline" aria-hidden=${!this.hasHeadline || D}>
              <slot
                name="headline"
                @slotchange=${this.handleHeadlineChange}></slot>
            </h2>
            <md-divider></md-divider>
          </div>
          <div class="scroller">
            <div class="content">
              <div class="top anchor"></div>
              <slot name="content"></slot>
              <div class="bottom anchor"></div>
            </div>
          </div>
          <div class="actions">
            <md-divider></md-divider>
            <slot name="actions" @slotchange=${this.handleActionsChange}></slot>
          </div>
        </div>
        ${showFocusTrap ? focusTrap : D}
      </dialog>
    `;
    }
    firstUpdated() {
      this.intersectionObserver = new IntersectionObserver((entries) => {
        for (const entry of entries) {
          this.handleAnchorIntersection(entry);
        }
      }, { root: this.scroller });
      this.intersectionObserver.observe(this.topAnchor);
      this.intersectionObserver.observe(this.bottomAnchor);
    }
    handleDialogClick() {
      if (this.nextClickIsFromContent) {
        this.nextClickIsFromContent = false;
        return;
      }
      const preventDefault = !this.dispatchEvent(new Event("cancel", { cancelable: true }));
      if (preventDefault) {
        return;
      }
      this.close();
    }
    handleContentClick() {
      this.nextClickIsFromContent = true;
    }
    handleSubmit(event) {
      const form = event.target;
      const { submitter } = event;
      if (form.method !== "dialog" || !submitter) {
        return;
      }
      this.close(submitter.getAttribute("value") ?? this.returnValue);
    }
    handleCancel(event) {
      if (event.target !== this.dialog) {
        return;
      }
      this.escapePressedWithoutCancel = false;
      const preventDefault = !redispatchEvent(this, event);
      event.preventDefault();
      if (preventDefault) {
        return;
      }
      this.close();
    }
    handleClose() {
      if (!this.escapePressedWithoutCancel) {
        return;
      }
      this.escapePressedWithoutCancel = false;
      this.dialog?.dispatchEvent(new Event("cancel", { cancelable: true }));
    }
    handleKeydown(event) {
      if (event.key !== "Escape") {
        return;
      }
      this.escapePressedWithoutCancel = true;
      setTimeout(() => {
        this.escapePressedWithoutCancel = false;
      });
    }
    async animateDialog(animation) {
      this.cancelAnimations?.abort();
      this.cancelAnimations = new AbortController();
      if (this.quick) {
        return;
      }
      const { dialog, scrim, container, headline, content, actions } = this;
      if (!dialog || !scrim || !container || !headline || !content || !actions) {
        return;
      }
      const { container: containerAnimate, dialog: dialogAnimate, scrim: scrimAnimate, headline: headlineAnimate, content: contentAnimate, actions: actionsAnimate } = animation;
      const elementAndAnimation = [
        [dialog, dialogAnimate ?? []],
        [scrim, scrimAnimate ?? []],
        [container, containerAnimate ?? []],
        [headline, headlineAnimate ?? []],
        [content, contentAnimate ?? []],
        [actions, actionsAnimate ?? []]
      ];
      const animations = [];
      for (const [element, animation2] of elementAndAnimation) {
        for (const animateArgs of animation2) {
          const animation3 = element.animate(...animateArgs);
          this.cancelAnimations.signal.addEventListener("abort", () => {
            animation3.cancel();
          });
          animations.push(animation3);
        }
      }
      await Promise.all(animations.map((animation2) => animation2.finished.catch(() => {
      })));
    }
    handleHeadlineChange(event) {
      const slot = event.target;
      this.hasHeadline = slot.assignedElements().length > 0;
    }
    handleActionsChange(event) {
      const slot = event.target;
      this.hasActions = slot.assignedElements().length > 0;
    }
    handleIconChange(event) {
      const slot = event.target;
      this.hasIcon = slot.assignedElements().length > 0;
    }
    handleAnchorIntersection(entry) {
      const { target, isIntersecting } = entry;
      if (target === this.topAnchor) {
        this.isAtScrollTop = isIntersecting;
      }
      if (target === this.bottomAnchor) {
        this.isAtScrollBottom = isIntersecting;
      }
    }
    getIsConnectedPromise() {
      return new Promise((resolve) => {
        this.isConnectedPromiseResolve = resolve;
      });
    }
    handleFocusTrapFocus(event) {
      const [firstFocusableChild, lastFocusableChild] = this.getFirstAndLastFocusableChildren();
      if (!firstFocusableChild || !lastFocusableChild) {
        this.dialog?.focus();
        return;
      }
      const isFirstFocusTrap = event.target === this.firstFocusTrap;
      const isLastFocusTrap = !isFirstFocusTrap;
      const focusCameFromFirstChild = event.relatedTarget === firstFocusableChild;
      const focusCameFromLastChild = event.relatedTarget === lastFocusableChild;
      const focusCameFromOutsideDialog = !focusCameFromFirstChild && !focusCameFromLastChild;
      const shouldFocusFirstChild = isLastFocusTrap && focusCameFromLastChild || isFirstFocusTrap && focusCameFromOutsideDialog;
      if (shouldFocusFirstChild) {
        firstFocusableChild.focus();
        return;
      }
      const shouldFocusLastChild = isFirstFocusTrap && focusCameFromFirstChild || isLastFocusTrap && focusCameFromOutsideDialog;
      if (shouldFocusLastChild) {
        lastFocusableChild.focus();
        return;
      }
    }
    getFirstAndLastFocusableChildren() {
      if (!this.treewalker) {
        return [null, null];
      }
      let firstFocusableChild = null;
      let lastFocusableChild = null;
      this.treewalker.currentNode = this.treewalker.root;
      while (this.treewalker.nextNode()) {
        const nextChild = this.treewalker.currentNode;
        if (!isFocusable(nextChild)) {
          continue;
        }
        if (!firstFocusableChild) {
          firstFocusableChild = nextChild;
        }
        lastFocusableChild = nextChild;
      }
      return [firstFocusableChild, lastFocusableChild];
    }
  };
  __decorate([
    n4({ type: Boolean })
  ], Dialog.prototype, "open", null);
  __decorate([
    n4({ type: Boolean })
  ], Dialog.prototype, "quick", void 0);
  __decorate([
    n4({ attribute: false })
  ], Dialog.prototype, "returnValue", void 0);
  __decorate([
    n4()
  ], Dialog.prototype, "type", void 0);
  __decorate([
    n4({ type: Boolean, attribute: "no-focus-trap" })
  ], Dialog.prototype, "noFocusTrap", void 0);
  __decorate([
    e4("dialog")
  ], Dialog.prototype, "dialog", void 0);
  __decorate([
    e4(".scrim")
  ], Dialog.prototype, "scrim", void 0);
  __decorate([
    e4(".container")
  ], Dialog.prototype, "container", void 0);
  __decorate([
    e4(".headline")
  ], Dialog.prototype, "headline", void 0);
  __decorate([
    e4(".content")
  ], Dialog.prototype, "content", void 0);
  __decorate([
    e4(".actions")
  ], Dialog.prototype, "actions", void 0);
  __decorate([
    r4()
  ], Dialog.prototype, "isAtScrollTop", void 0);
  __decorate([
    r4()
  ], Dialog.prototype, "isAtScrollBottom", void 0);
  __decorate([
    e4(".scroller")
  ], Dialog.prototype, "scroller", void 0);
  __decorate([
    e4(".top.anchor")
  ], Dialog.prototype, "topAnchor", void 0);
  __decorate([
    e4(".bottom.anchor")
  ], Dialog.prototype, "bottomAnchor", void 0);
  __decorate([
    e4(".focus-trap")
  ], Dialog.prototype, "firstFocusTrap", void 0);
  __decorate([
    r4()
  ], Dialog.prototype, "hasHeadline", void 0);
  __decorate([
    r4()
  ], Dialog.prototype, "hasActions", void 0);
  __decorate([
    r4()
  ], Dialog.prototype, "hasIcon", void 0);
  function isFocusable(element) {
    const knownFocusableElements = ":is(button,input,select,textarea,object,:is(a,area)[href],[tabindex],[contenteditable=true])";
    const notDisabled = ":not(:disabled,[disabled])";
    const notNegativeTabIndex = ':not([tabindex^="-"])';
    if (element.matches(knownFocusableElements + notDisabled + notNegativeTabIndex)) {
      return true;
    }
    const isCustomElement = element.localName.includes("-");
    if (!isCustomElement) {
      return false;
    }
    if (!element.matches(notDisabled)) {
      return false;
    }
    return element.shadowRoot?.delegatesFocus ?? false;
  }

  // node_modules/@material/web/dialog/internal/dialog-styles.js
  var styles10 = i`:host{border-start-start-radius:var(--md-dialog-container-shape-start-start, var(--md-dialog-container-shape, var(--md-sys-shape-corner-extra-large, 28px)));border-start-end-radius:var(--md-dialog-container-shape-start-end, var(--md-dialog-container-shape, var(--md-sys-shape-corner-extra-large, 28px)));border-end-end-radius:var(--md-dialog-container-shape-end-end, var(--md-dialog-container-shape, var(--md-sys-shape-corner-extra-large, 28px)));border-end-start-radius:var(--md-dialog-container-shape-end-start, var(--md-dialog-container-shape, var(--md-sys-shape-corner-extra-large, 28px)));display:contents;margin:auto;max-height:min(560px,100% - 48px);max-width:min(560px,100% - 48px);min-height:140px;min-width:280px;position:fixed;height:fit-content;width:fit-content}dialog{background:rgba(0,0,0,0);border:none;border-radius:inherit;flex-direction:column;height:inherit;margin:inherit;max-height:inherit;max-width:inherit;min-height:inherit;min-width:inherit;outline:none;overflow:visible;padding:0;width:inherit}dialog[open]{display:flex}::backdrop{background:none}.scrim{background:var(--md-sys-color-scrim, #000);display:none;inset:0;opacity:32%;pointer-events:none;position:fixed;z-index:1}:host([open]) .scrim{display:flex}h2{all:unset;align-self:stretch}.headline{align-items:center;color:var(--md-dialog-headline-color, var(--md-sys-color-on-surface, #1d1b20));display:flex;flex-direction:column;font-family:var(--md-dialog-headline-font, var(--md-sys-typescale-headline-small-font, var(--md-ref-typeface-brand, Roboto)));font-size:var(--md-dialog-headline-size, var(--md-sys-typescale-headline-small-size, 1.5rem));line-height:var(--md-dialog-headline-line-height, var(--md-sys-typescale-headline-small-line-height, 2rem));font-weight:var(--md-dialog-headline-weight, var(--md-sys-typescale-headline-small-weight, var(--md-ref-typeface-weight-regular, 400)));position:relative}slot[name=headline]::slotted(*){align-items:center;align-self:stretch;box-sizing:border-box;display:flex;gap:8px;padding:24px 24px 0}.icon{display:flex}slot[name=icon]::slotted(*){color:var(--md-dialog-icon-color, var(--md-sys-color-secondary, #625b71));fill:currentColor;font-size:var(--md-dialog-icon-size, 24px);margin-top:24px;height:var(--md-dialog-icon-size, 24px);width:var(--md-dialog-icon-size, 24px)}.has-icon slot[name=headline]::slotted(*){justify-content:center;padding-top:16px}.scrollable slot[name=headline]::slotted(*){padding-bottom:16px}.scrollable.has-headline slot[name=content]::slotted(*){padding-top:8px}.container{border-radius:inherit;display:flex;flex-direction:column;flex-grow:1;overflow:hidden;position:relative;transform-origin:top}.container::before{background:var(--md-dialog-container-color, var(--md-sys-color-surface-container-high, #ece6f0));border-radius:inherit;content:"";inset:0;position:absolute}.scroller{display:flex;flex:1;flex-direction:column;overflow:hidden;z-index:1}.scrollable .scroller{overflow-y:scroll}.content{color:var(--md-dialog-supporting-text-color, var(--md-sys-color-on-surface-variant, #49454f));font-family:var(--md-dialog-supporting-text-font, var(--md-sys-typescale-body-medium-font, var(--md-ref-typeface-plain, Roboto)));font-size:var(--md-dialog-supporting-text-size, var(--md-sys-typescale-body-medium-size, 0.875rem));line-height:var(--md-dialog-supporting-text-line-height, var(--md-sys-typescale-body-medium-line-height, 1.25rem));flex:1;font-weight:var(--md-dialog-supporting-text-weight, var(--md-sys-typescale-body-medium-weight, var(--md-ref-typeface-weight-regular, 400)));height:min-content;position:relative}slot[name=content]::slotted(*){box-sizing:border-box;padding:24px}.anchor{position:absolute}.top.anchor{top:0}.bottom.anchor{bottom:0}.actions{position:relative}slot[name=actions]::slotted(*){box-sizing:border-box;display:flex;gap:8px;justify-content:flex-end;padding:16px 24px 24px}.has-actions slot[name=content]::slotted(*){padding-bottom:8px}md-divider{display:none;position:absolute}.has-headline.show-top-divider .headline md-divider,.has-actions.show-bottom-divider .actions md-divider{display:flex}.headline md-divider{bottom:0}.actions md-divider{top:0}@media(forced-colors: active){dialog{outline:2px solid WindowText}}
`;

  // node_modules/@material/web/dialog/dialog.js
  var MdDialog = class MdDialog2 extends Dialog {
  };
  MdDialog.styles = [styles10];
  MdDialog = __decorate([
    t2("md-dialog")
  ], MdDialog);

  // node_modules/@material/web/icon/internal/icon.js
  var Icon = class extends h3 {
    render() {
      return ke`<slot></slot>`;
    }
    connectedCallback() {
      super.connectedCallback();
      const ariaHidden = this.getAttribute("aria-hidden");
      if (ariaHidden === "false") {
        this.removeAttribute("aria-hidden");
        return;
      }
      this.setAttribute("aria-hidden", "true");
    }
  };

  // node_modules/@material/web/icon/internal/icon-styles.js
  var styles11 = i`:host{font-size:var(--md-icon-size, 24px);width:var(--md-icon-size, 24px);height:var(--md-icon-size, 24px);color:inherit;font-variation-settings:inherit;font-weight:400;font-family:var(--md-icon-font, Material Symbols Outlined);display:inline-flex;font-style:normal;place-items:center;place-content:center;line-height:1;overflow:hidden;letter-spacing:normal;text-transform:none;user-select:none;white-space:nowrap;word-wrap:normal;flex-shrink:0;-webkit-font-smoothing:antialiased;text-rendering:optimizeLegibility;-moz-osx-font-smoothing:grayscale}::slotted(svg){fill:currentColor}::slotted(*){height:100%;width:100%}
`;

  // node_modules/@material/web/icon/icon.js
  var MdIcon = class MdIcon2 extends Icon {
  };
  MdIcon.styles = [styles11];
  MdIcon = __decorate([
    t2("md-icon")
  ], MdIcon);

  // node_modules/lit-html/static.js
  var $e = Symbol.for("");
  var xe = (t4) => {
    if (t4?.r === $e)
      return t4?._$litStatic$;
  };
  var er = (t4, ...r5) => ({ _$litStatic$: r5.reduce((r6, e6, a2) => r6 + ((t5) => {
    if (void 0 !== t5._$litStatic$)
      return t5._$litStatic$;
    throw Error(`Value passed to 'literal' function must be a 'literal' result: ${t5}. Use 'unsafeStatic' to pass non-literal values, but
            take care to ensure page security.`);
  })(e6) + t4[a2 + 1], t4[0]), r: $e });
  var Te = /* @__PURE__ */ new Map();
  var Ee = (t4) => (r5, ...e6) => {
    const a2 = e6.length;
    let o5, s2;
    const i4 = [], l2 = [];
    let n5, u2 = 0, c4 = false;
    for (; u2 < a2; ) {
      for (n5 = r5[u2]; u2 < a2 && void 0 !== (s2 = e6[u2], o5 = xe(s2)); )
        n5 += o5 + r5[++u2], c4 = true;
      u2 !== a2 && l2.push(s2), i4.push(n5), u2++;
    }
    if (u2 === a2 && i4.push(r5[a2]), c4) {
      const t5 = i4.join("$$lit$$");
      void 0 === (r5 = Te.get(t5)) && (i4.raw = i4, Te.set(t5, r5 = i4)), e6 = l2;
    }
    return t4(r5, ...e6);
  };
  var ke2 = Ee(ke);
  var Oe2 = Ee(Oe);
  var Se2 = Ee(Se);

  // node_modules/@material/web/internal/controller/is-rtl.js
  function isRtl(el, shouldCheck = true) {
    return shouldCheck && getComputedStyle(el).getPropertyValue("direction").trim() === "rtl";
  }

  // node_modules/@material/web/iconbutton/internal/icon-button.js
  var iconButtonBaseClass = mixinDelegatesAria(mixinElementInternals(h3));
  var IconButton = class extends iconButtonBaseClass {
    get name() {
      return this.getAttribute("name") ?? "";
    }
    set name(name) {
      this.setAttribute("name", name);
    }
    /**
     * The associated form element with which this element's value will submit.
     */
    get form() {
      return this[internals].form;
    }
    /**
     * The labels this element is associated with.
     */
    get labels() {
      return this[internals].labels;
    }
    constructor() {
      super();
      this.disabled = false;
      this.softDisabled = false;
      this.flipIconInRtl = false;
      this.href = "";
      this.target = "";
      this.ariaLabelSelected = "";
      this.toggle = false;
      this.selected = false;
      this.type = "submit";
      this.value = "";
      this.flipIcon = isRtl(this, this.flipIconInRtl);
      if (!co) {
        this.addEventListener("click", this.handleClick.bind(this));
      }
    }
    willUpdate() {
      if (this.href) {
        this.disabled = false;
        this.softDisabled = false;
      }
    }
    render() {
      const tag = this.href ? er`div` : er`button`;
      const { ariaLabel, ariaHasPopup, ariaExpanded } = this;
      const hasToggledAriaLabel = ariaLabel && this.ariaLabelSelected;
      const ariaPressedValue = !this.toggle ? D : this.selected;
      let ariaLabelValue = D;
      if (!this.href) {
        ariaLabelValue = hasToggledAriaLabel && this.selected ? this.ariaLabelSelected : ariaLabel;
      }
      return ke2`<${tag}
        class="icon-button ${Rt(this.getRenderClasses())}"
        id="button"
        aria-label="${ariaLabelValue || D}"
        aria-haspopup="${!this.href && ariaHasPopup || D}"
        aria-expanded="${!this.href && ariaExpanded || D}"
        aria-pressed="${ariaPressedValue}"
        aria-disabled=${!this.href && this.softDisabled || D}
        ?disabled="${!this.href && this.disabled}"
        @click="${this.handleClickOnChild}">
        ${this.renderFocusRing()}
        ${this.renderRipple()}
        ${!this.selected ? this.renderIcon() : D}
        ${this.selected ? this.renderSelectedIcon() : D}
        ${this.renderTouchTarget()}
        ${this.href && this.renderLink()}
  </${tag}>`;
    }
    renderLink() {
      const { ariaLabel } = this;
      return ke`
      <a
        class="link"
        id="link"
        href="${this.href}"
        target="${this.target || D}"
        aria-label="${ariaLabel || D}"></a>
    `;
    }
    getRenderClasses() {
      return {
        "flip-icon": this.flipIcon,
        "selected": this.toggle && this.selected
      };
    }
    renderIcon() {
      return ke`<span class="icon"><slot></slot></span>`;
    }
    renderSelectedIcon() {
      return ke`<span class="icon icon--selected"
      ><slot name="selected"><slot></slot></slot
    ></span>`;
    }
    renderTouchTarget() {
      return ke`<span class="touch"></span>`;
    }
    renderFocusRing() {
      return ke`<md-focus-ring
      part="focus-ring"
      for=${this.href ? "link" : "button"}></md-focus-ring>`;
    }
    renderRipple() {
      const isRippleDisabled = !this.href && (this.disabled || this.softDisabled);
      return ke`<md-ripple
      for=${this.href ? "link" : D}
      ?disabled="${isRippleDisabled}"></md-ripple>`;
    }
    connectedCallback() {
      this.flipIcon = isRtl(this, this.flipIconInRtl);
      super.connectedCallback();
    }
    /** Handles a click on this element. */
    handleClick(event) {
      if (!this.href && this.softDisabled) {
        event.stopImmediatePropagation();
        event.preventDefault();
        return;
      }
    }
    /**
     * Handles a click on the child <div> or <button> element within this
     * element's shadow DOM.
     */
    async handleClickOnChild(event) {
      await 0;
      if (!this.toggle || this.disabled || this.softDisabled || event.defaultPrevented) {
        return;
      }
      this.selected = !this.selected;
      this.dispatchEvent(new InputEvent("input", { bubbles: true, composed: true }));
      this.dispatchEvent(new Event("change", { bubbles: true }));
    }
  };
  (() => {
    setupFormSubmitter(IconButton);
  })();
  IconButton.formAssociated = true;
  IconButton.shadowRootOptions = {
    mode: "open",
    delegatesFocus: true
  };
  __decorate([
    n4({ type: Boolean, reflect: true })
  ], IconButton.prototype, "disabled", void 0);
  __decorate([
    n4({ type: Boolean, attribute: "soft-disabled", reflect: true })
  ], IconButton.prototype, "softDisabled", void 0);
  __decorate([
    n4({ type: Boolean, attribute: "flip-icon-in-rtl" })
  ], IconButton.prototype, "flipIconInRtl", void 0);
  __decorate([
    n4()
  ], IconButton.prototype, "href", void 0);
  __decorate([
    n4()
  ], IconButton.prototype, "target", void 0);
  __decorate([
    n4({ attribute: "aria-label-selected" })
  ], IconButton.prototype, "ariaLabelSelected", void 0);
  __decorate([
    n4({ type: Boolean })
  ], IconButton.prototype, "toggle", void 0);
  __decorate([
    n4({ type: Boolean, reflect: true })
  ], IconButton.prototype, "selected", void 0);
  __decorate([
    n4()
  ], IconButton.prototype, "type", void 0);
  __decorate([
    n4({ reflect: true })
  ], IconButton.prototype, "value", void 0);
  __decorate([
    r4()
  ], IconButton.prototype, "flipIcon", void 0);

  // node_modules/@material/web/iconbutton/internal/shared-styles.js
  var styles12 = i`:host{display:inline-flex;outline:none;-webkit-tap-highlight-color:rgba(0,0,0,0);height:var(--_container-height);width:var(--_container-width);justify-content:center}:host([touch-target=wrapper]){margin:max(0px,(48px - var(--_container-height))/2) max(0px,(48px - var(--_container-width))/2)}md-focus-ring{--md-focus-ring-shape-start-start: var(--_container-shape-start-start);--md-focus-ring-shape-start-end: var(--_container-shape-start-end);--md-focus-ring-shape-end-end: var(--_container-shape-end-end);--md-focus-ring-shape-end-start: var(--_container-shape-end-start)}:host(:is([disabled],[soft-disabled])){pointer-events:none}.icon-button{place-items:center;background:none;border:none;box-sizing:border-box;cursor:pointer;display:flex;place-content:center;outline:none;padding:0;position:relative;text-decoration:none;user-select:none;z-index:0;flex:1;border-start-start-radius:var(--_container-shape-start-start);border-start-end-radius:var(--_container-shape-start-end);border-end-start-radius:var(--_container-shape-end-start);border-end-end-radius:var(--_container-shape-end-end)}.icon ::slotted(*){font-size:var(--_icon-size);height:var(--_icon-size);width:var(--_icon-size);font-weight:inherit}md-ripple{z-index:-1;border-start-start-radius:var(--_container-shape-start-start);border-start-end-radius:var(--_container-shape-start-end);border-end-start-radius:var(--_container-shape-end-start);border-end-end-radius:var(--_container-shape-end-end)}.flip-icon .icon{transform:scaleX(-1)}.icon{display:inline-flex}.link{height:100%;outline:none;position:absolute;width:100%}.touch{position:absolute;height:max(48px,100%);width:max(48px,100%)}:host([touch-target=none]) .touch{display:none}@media(forced-colors: active){:host(:is([disabled],[soft-disabled])){--_disabled-icon-color: GrayText;--_disabled-icon-opacity: 1}}
`;

  // node_modules/@material/web/iconbutton/internal/standard-styles.js
  var styles13 = i`:host{--_disabled-icon-color: var(--md-icon-button-disabled-icon-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-icon-opacity: var(--md-icon-button-disabled-icon-opacity, 0.38);--_icon-size: var(--md-icon-button-icon-size, 24px);--_selected-focus-icon-color: var(--md-icon-button-selected-focus-icon-color, var(--md-sys-color-primary, #6750a4));--_selected-hover-icon-color: var(--md-icon-button-selected-hover-icon-color, var(--md-sys-color-primary, #6750a4));--_selected-hover-state-layer-color: var(--md-icon-button-selected-hover-state-layer-color, var(--md-sys-color-primary, #6750a4));--_selected-hover-state-layer-opacity: var(--md-icon-button-selected-hover-state-layer-opacity, 0.08);--_selected-icon-color: var(--md-icon-button-selected-icon-color, var(--md-sys-color-primary, #6750a4));--_selected-pressed-icon-color: var(--md-icon-button-selected-pressed-icon-color, var(--md-sys-color-primary, #6750a4));--_selected-pressed-state-layer-color: var(--md-icon-button-selected-pressed-state-layer-color, var(--md-sys-color-primary, #6750a4));--_selected-pressed-state-layer-opacity: var(--md-icon-button-selected-pressed-state-layer-opacity, 0.12);--_state-layer-height: var(--md-icon-button-state-layer-height, 40px);--_state-layer-shape: var(--md-icon-button-state-layer-shape, var(--md-sys-shape-corner-full, 9999px));--_state-layer-width: var(--md-icon-button-state-layer-width, 40px);--_focus-icon-color: var(--md-icon-button-focus-icon-color, var(--md-sys-color-on-surface-variant, #49454f));--_hover-icon-color: var(--md-icon-button-hover-icon-color, var(--md-sys-color-on-surface-variant, #49454f));--_hover-state-layer-color: var(--md-icon-button-hover-state-layer-color, var(--md-sys-color-on-surface-variant, #49454f));--_hover-state-layer-opacity: var(--md-icon-button-hover-state-layer-opacity, 0.08);--_icon-color: var(--md-icon-button-icon-color, var(--md-sys-color-on-surface-variant, #49454f));--_pressed-icon-color: var(--md-icon-button-pressed-icon-color, var(--md-sys-color-on-surface-variant, #49454f));--_pressed-state-layer-color: var(--md-icon-button-pressed-state-layer-color, var(--md-sys-color-on-surface-variant, #49454f));--_pressed-state-layer-opacity: var(--md-icon-button-pressed-state-layer-opacity, 0.12);--_container-shape-start-start: 0;--_container-shape-start-end: 0;--_container-shape-end-end: 0;--_container-shape-end-start: 0;--_container-height: 0;--_container-width: 0;height:var(--_state-layer-height);width:var(--_state-layer-width)}:host([touch-target=wrapper]){margin:max(0px,(48px - var(--_state-layer-height))/2) max(0px,(48px - var(--_state-layer-width))/2)}md-focus-ring{--md-focus-ring-shape-start-start: var(--_state-layer-shape);--md-focus-ring-shape-start-end: var(--_state-layer-shape);--md-focus-ring-shape-end-end: var(--_state-layer-shape);--md-focus-ring-shape-end-start: var(--_state-layer-shape)}.standard{background-color:rgba(0,0,0,0);color:var(--_icon-color);--md-ripple-hover-color: var(--_hover-state-layer-color);--md-ripple-hover-opacity: var(--_hover-state-layer-opacity);--md-ripple-pressed-color: var(--_pressed-state-layer-color);--md-ripple-pressed-opacity: var(--_pressed-state-layer-opacity)}.standard:hover{color:var(--_hover-icon-color)}.standard:focus{color:var(--_focus-icon-color)}.standard:active{color:var(--_pressed-icon-color)}.standard:is(:disabled,[aria-disabled=true]){color:var(--_disabled-icon-color)}md-ripple{border-radius:var(--_state-layer-shape)}.standard:is(:disabled,[aria-disabled=true]){opacity:var(--_disabled-icon-opacity)}.selected{--md-ripple-hover-color: var(--_selected-hover-state-layer-color);--md-ripple-hover-opacity: var(--_selected-hover-state-layer-opacity);--md-ripple-pressed-color: var(--_selected-pressed-state-layer-color);--md-ripple-pressed-opacity: var(--_selected-pressed-state-layer-opacity)}.selected:not(:disabled,[aria-disabled=true]){color:var(--_selected-icon-color)}.selected:not(:disabled,[aria-disabled=true]):hover{color:var(--_selected-hover-icon-color)}.selected:not(:disabled,[aria-disabled=true]):focus{color:var(--_selected-focus-icon-color)}.selected:not(:disabled,[aria-disabled=true]):active{color:var(--_selected-pressed-icon-color)}
`;

  // node_modules/@material/web/iconbutton/icon-button.js
  var MdIconButton = class MdIconButton2 extends IconButton {
    getRenderClasses() {
      return {
        ...super.getRenderClasses(),
        "standard": true
      };
    }
  };
  MdIconButton.styles = [styles12, styles13];
  MdIconButton = __decorate([
    t2("md-icon-button")
  ], MdIconButton);

  // node_modules/@material/web/labs/behaviors/constraint-validation.js
  var createValidator = Symbol("createValidator");
  var getValidityAnchor = Symbol("getValidityAnchor");
  var privateValidator = Symbol("privateValidator");
  var privateSyncValidity = Symbol("privateSyncValidity");
  var privateCustomValidationMessage = Symbol("privateCustomValidationMessage");
  function mixinConstraintValidation(base) {
    var _a2;
    class ConstraintValidationElement extends base {
      constructor() {
        super(...arguments);
        this[_a2] = "";
      }
      get validity() {
        this[privateSyncValidity]();
        return this[internals].validity;
      }
      get validationMessage() {
        this[privateSyncValidity]();
        return this[internals].validationMessage;
      }
      get willValidate() {
        this[privateSyncValidity]();
        return this[internals].willValidate;
      }
      checkValidity() {
        this[privateSyncValidity]();
        return this[internals].checkValidity();
      }
      reportValidity() {
        this[privateSyncValidity]();
        return this[internals].reportValidity();
      }
      setCustomValidity(error) {
        this[privateCustomValidationMessage] = error;
        this[privateSyncValidity]();
      }
      requestUpdate(name, oldValue, options) {
        super.requestUpdate(name, oldValue, options);
        this[privateSyncValidity]();
      }
      firstUpdated(changed) {
        super.firstUpdated(changed);
        this[privateSyncValidity]();
      }
      [(_a2 = privateCustomValidationMessage, privateSyncValidity)]() {
        if (co) {
          return;
        }
        if (!this[privateValidator]) {
          this[privateValidator] = this[createValidator]();
        }
        const { validity, validationMessage: nonCustomValidationMessage } = this[privateValidator].getValidity();
        const customError = !!this[privateCustomValidationMessage];
        const validationMessage = this[privateCustomValidationMessage] || nonCustomValidationMessage;
        this[internals].setValidity({ ...validity, customError }, validationMessage, this[getValidityAnchor]() ?? void 0);
      }
      [createValidator]() {
        throw new Error("Implement [createValidator]");
      }
      [getValidityAnchor]() {
        throw new Error("Implement [getValidityAnchor]");
      }
    }
    return ConstraintValidationElement;
  }

  // node_modules/@material/web/labs/behaviors/focusable.js
  var isFocusable2 = Symbol("isFocusable");
  var privateIsFocusable = Symbol("privateIsFocusable");
  var externalTabIndex = Symbol("externalTabIndex");
  var isUpdatingTabIndex = Symbol("isUpdatingTabIndex");
  var updateTabIndex = Symbol("updateTabIndex");
  function mixinFocusable(base) {
    var _a2, _b, _c;
    class FocusableElement extends base {
      constructor() {
        super(...arguments);
        this[_a2] = true;
        this[_b] = null;
        this[_c] = false;
      }
      get [isFocusable2]() {
        return this[privateIsFocusable];
      }
      set [isFocusable2](value) {
        if (this[isFocusable2] === value) {
          return;
        }
        this[privateIsFocusable] = value;
        this[updateTabIndex]();
      }
      connectedCallback() {
        super.connectedCallback();
        this[updateTabIndex]();
      }
      attributeChangedCallback(name, old, value) {
        if (name !== "tabindex") {
          super.attributeChangedCallback(name, old, value);
          return;
        }
        this.requestUpdate("tabIndex", Number(old ?? -1));
        if (this[isUpdatingTabIndex]) {
          return;
        }
        if (!this.hasAttribute("tabindex")) {
          this[externalTabIndex] = null;
          this[updateTabIndex]();
          return;
        }
        this[externalTabIndex] = this.tabIndex;
      }
      [(_a2 = privateIsFocusable, _b = externalTabIndex, _c = isUpdatingTabIndex, updateTabIndex)]() {
        const internalTabIndex = this[isFocusable2] ? 0 : -1;
        const computedTabIndex = this[externalTabIndex] ?? internalTabIndex;
        this[isUpdatingTabIndex] = true;
        this.tabIndex = computedTabIndex;
        this[isUpdatingTabIndex] = false;
      }
    }
    __decorate([
      n4({ noAccessor: true })
    ], FocusableElement.prototype, "tabIndex", void 0);
    return FocusableElement;
  }

  // node_modules/@material/web/labs/behaviors/form-associated.js
  var getFormValue = Symbol("getFormValue");
  var getFormState = Symbol("getFormState");
  function mixinFormAssociated(base) {
    class FormAssociatedElement extends base {
      get form() {
        return this[internals].form;
      }
      get labels() {
        return this[internals].labels;
      }
      // Use @property for the `name` and `disabled` properties to add them to the
      // `observedAttributes` array and trigger `attributeChangedCallback()`.
      //
      // We don't use Lit's default getter/setter (`noAccessor: true`) because
      // the attributes need to be updated synchronously to work with synchronous
      // form APIs, and Lit updates attributes async by default.
      get name() {
        return this.getAttribute("name") ?? "";
      }
      set name(name) {
        this.setAttribute("name", name);
      }
      get disabled() {
        return this.hasAttribute("disabled");
      }
      set disabled(disabled) {
        this.toggleAttribute("disabled", disabled);
      }
      attributeChangedCallback(name, old, value) {
        if (name === "name" || name === "disabled") {
          const oldValue = name === "disabled" ? old !== null : old;
          this.requestUpdate(name, oldValue);
          return;
        }
        super.attributeChangedCallback(name, old, value);
      }
      requestUpdate(name, oldValue, options) {
        super.requestUpdate(name, oldValue, options);
        this[internals].setFormValue(this[getFormValue](), this[getFormState]());
      }
      [getFormValue]() {
        throw new Error("Implement [getFormValue]");
      }
      [getFormState]() {
        return this[getFormValue]();
      }
      formDisabledCallback(disabled) {
        this.disabled = disabled;
      }
    }
    FormAssociatedElement.formAssociated = true;
    __decorate([
      n4({ noAccessor: true })
    ], FormAssociatedElement.prototype, "name", null);
    __decorate([
      n4({ type: Boolean, noAccessor: true })
    ], FormAssociatedElement.prototype, "disabled", null);
    return FormAssociatedElement;
  }

  // node_modules/@material/web/labs/behaviors/validators/validator.js
  var Validator = class {
    /**
     * Creates a new validator.
     *
     * @param getCurrentState A callback that returns the current state of
     *     constraint validation-related properties.
     */
    constructor(getCurrentState) {
      this.getCurrentState = getCurrentState;
      this.currentValidity = {
        validity: {},
        validationMessage: ""
      };
    }
    /**
     * Returns the current `ValidityStateFlags` and validation message for the
     * validator.
     *
     * If the constraint validation state has not changed, this will return a
     * cached result. This is important since `getValidity()` can be called
     * frequently in response to synchronous property changes.
     *
     * @return The current validity and validation message.
     */
    getValidity() {
      const state = this.getCurrentState();
      const hasStateChanged = !this.prevState || !this.equals(this.prevState, state);
      if (!hasStateChanged) {
        return this.currentValidity;
      }
      const { validity, validationMessage } = this.computeValidity(state);
      this.prevState = this.copy(state);
      this.currentValidity = {
        validationMessage,
        validity: {
          // Change any `ValidityState` instances into `ValidityStateFlags` since
          // `ValidityState` cannot be easily `{...spread}`.
          badInput: validity.badInput,
          customError: validity.customError,
          patternMismatch: validity.patternMismatch,
          rangeOverflow: validity.rangeOverflow,
          rangeUnderflow: validity.rangeUnderflow,
          stepMismatch: validity.stepMismatch,
          tooLong: validity.tooLong,
          tooShort: validity.tooShort,
          typeMismatch: validity.typeMismatch,
          valueMissing: validity.valueMissing
        }
      };
      return this.currentValidity;
    }
  };

  // node_modules/@material/web/labs/behaviors/validators/radio-validator.js
  var RadioValidator = class extends Validator {
    computeValidity(states) {
      if (!this.radioElement) {
        this.radioElement = document.createElement("input");
        this.radioElement.type = "radio";
        this.radioElement.name = "group";
      }
      let isRequired = false;
      let isChecked = false;
      for (const { checked, required } of states) {
        if (required) {
          isRequired = true;
        }
        if (checked) {
          isChecked = true;
        }
      }
      this.radioElement.checked = isChecked;
      this.radioElement.required = isRequired;
      return {
        validity: {
          valueMissing: isRequired && !isChecked
        },
        validationMessage: this.radioElement.validationMessage
      };
    }
    equals(prevGroup, nextGroup) {
      if (prevGroup.length !== nextGroup.length) {
        return false;
      }
      for (let i4 = 0; i4 < prevGroup.length; i4++) {
        const prev = prevGroup[i4];
        const next = nextGroup[i4];
        if (prev.checked !== next.checked || prev.required !== next.required) {
          return false;
        }
      }
      return true;
    }
    copy(states) {
      return states.map(({ checked, required }) => ({
        checked,
        required
      }));
    }
  };

  // node_modules/@material/web/radio/internal/single-selection-controller.js
  var SingleSelectionController = class {
    /**
     * All single selection elements in the host element's root with the same
     * `name` attribute, including the host element.
     */
    get controls() {
      const name = this.host.getAttribute("name");
      if (!name || !this.root || !this.host.isConnected) {
        return [this.host];
      }
      return Array.from(this.root.querySelectorAll(`[name="${name}"]`));
    }
    constructor(host) {
      this.host = host;
      this.focused = false;
      this.root = null;
      this.handleFocusIn = () => {
        this.focused = true;
        this.updateTabIndices();
      };
      this.handleFocusOut = () => {
        this.focused = false;
        this.updateTabIndices();
      };
      this.handleKeyDown = (event) => {
        const isDown = event.key === "ArrowDown";
        const isUp = event.key === "ArrowUp";
        const isLeft = event.key === "ArrowLeft";
        const isRight = event.key === "ArrowRight";
        if (!isLeft && !isRight && !isDown && !isUp) {
          return;
        }
        const siblings = this.controls;
        if (!siblings.length) {
          return;
        }
        event.preventDefault();
        const isRtl2 = getComputedStyle(this.host).direction === "rtl";
        const forwards = isRtl2 ? isLeft || isDown : isRight || isDown;
        const hostIndex = siblings.indexOf(this.host);
        let nextIndex = forwards ? hostIndex + 1 : hostIndex - 1;
        while (nextIndex !== hostIndex) {
          if (nextIndex >= siblings.length) {
            nextIndex = 0;
          } else if (nextIndex < 0) {
            nextIndex = siblings.length - 1;
          }
          const nextSibling = siblings[nextIndex];
          if (nextSibling.hasAttribute("disabled")) {
            if (forwards) {
              nextIndex++;
            } else {
              nextIndex--;
            }
            continue;
          }
          for (const sibling of siblings) {
            if (sibling !== nextSibling) {
              sibling.checked = false;
              sibling.tabIndex = -1;
              sibling.blur();
            }
          }
          nextSibling.checked = true;
          nextSibling.tabIndex = 0;
          nextSibling.focus();
          nextSibling.dispatchEvent(new Event("change", { bubbles: true }));
          break;
        }
      };
    }
    hostConnected() {
      this.root = this.host.getRootNode();
      this.host.addEventListener("keydown", this.handleKeyDown);
      this.host.addEventListener("focusin", this.handleFocusIn);
      this.host.addEventListener("focusout", this.handleFocusOut);
      if (this.host.checked) {
        this.uncheckSiblings();
      }
      this.updateTabIndices();
    }
    hostDisconnected() {
      this.host.removeEventListener("keydown", this.handleKeyDown);
      this.host.removeEventListener("focusin", this.handleFocusIn);
      this.host.removeEventListener("focusout", this.handleFocusOut);
      this.updateTabIndices();
      this.root = null;
    }
    /**
     * Should be called whenever the host's `checked` property changes
     * synchronously.
     */
    handleCheckedChange() {
      if (!this.host.checked) {
        return;
      }
      this.uncheckSiblings();
      this.updateTabIndices();
    }
    uncheckSiblings() {
      for (const sibling of this.controls) {
        if (sibling !== this.host) {
          sibling.checked = false;
        }
      }
    }
    /**
     * Updates the `tabindex` of the host and its siblings.
     */
    updateTabIndices() {
      const siblings = this.controls;
      const checkedSibling = siblings.find((sibling) => sibling.checked);
      if (checkedSibling || this.focused) {
        const focusable = checkedSibling || this.host;
        focusable.tabIndex = 0;
        for (const sibling of siblings) {
          if (sibling !== focusable) {
            sibling.tabIndex = -1;
          }
        }
        return;
      }
      for (const sibling of siblings) {
        sibling.tabIndex = 0;
      }
    }
  };

  // node_modules/@material/web/radio/internal/radio.js
  var _a;
  var CHECKED = Symbol("checked");
  var maskId = 0;
  var radioBaseClass = mixinConstraintValidation(mixinFormAssociated(mixinElementInternals(mixinFocusable(h3))));
  var Radio = class extends radioBaseClass {
    /**
     * Whether or not the radio is selected.
     */
    get checked() {
      return this[CHECKED];
    }
    set checked(checked) {
      const wasChecked = this.checked;
      if (wasChecked === checked) {
        return;
      }
      this[CHECKED] = checked;
      this.requestUpdate("checked", wasChecked);
      this.selectionController.handleCheckedChange();
    }
    constructor() {
      super();
      this.maskId = `cutout${++maskId}`;
      this[_a] = false;
      this.required = false;
      this.value = "on";
      this.selectionController = new SingleSelectionController(this);
      this.addController(this.selectionController);
      if (!co) {
        this[internals].role = "radio";
        this.addEventListener("click", this.handleClick.bind(this));
        this.addEventListener("keydown", this.handleKeydown.bind(this));
      }
    }
    render() {
      const classes = { "checked": this.checked };
      return ke`
      <div class="container ${Rt(classes)}" aria-hidden="true">
        <md-ripple
          part="ripple"
          .control=${this}
          ?disabled=${this.disabled}></md-ripple>
        <md-focus-ring part="focus-ring" .control=${this}></md-focus-ring>
        <svg class="icon" viewBox="0 0 20 20">
          <mask id="${this.maskId}">
            <rect width="100%" height="100%" fill="white" />
            <circle cx="10" cy="10" r="8" fill="black" />
          </mask>
          <circle
            class="outer circle"
            cx="10"
            cy="10"
            r="10"
            mask="url(#${this.maskId})" />
          <circle class="inner circle" cx="10" cy="10" r="5" />
        </svg>

        <input
          id="input"
          type="radio"
          tabindex="-1"
          .checked=${this.checked}
          .value=${this.value}
          ?disabled=${this.disabled} />
      </div>
    `;
    }
    updated() {
      this[internals].ariaChecked = String(this.checked);
    }
    async handleClick(event) {
      if (this.disabled) {
        return;
      }
      await 0;
      if (event.defaultPrevented) {
        return;
      }
      if (isActivationClick(event)) {
        this.focus();
      }
      this.checked = true;
      this.dispatchEvent(new Event("change", { bubbles: true }));
      this.dispatchEvent(new InputEvent("input", { bubbles: true, composed: true }));
    }
    async handleKeydown(event) {
      await 0;
      if (event.key !== " " || event.defaultPrevented) {
        return;
      }
      this.click();
    }
    [(_a = CHECKED, getFormValue)]() {
      return this.checked ? this.value : null;
    }
    [getFormState]() {
      return String(this.checked);
    }
    formResetCallback() {
      this.checked = this.hasAttribute("checked");
    }
    formStateRestoreCallback(state) {
      this.checked = state === "true";
    }
    [createValidator]() {
      return new RadioValidator(() => {
        if (!this.selectionController) {
          return [this];
        }
        return this.selectionController.controls;
      });
    }
    [getValidityAnchor]() {
      return this.container;
    }
  };
  __decorate([
    n4({ type: Boolean })
  ], Radio.prototype, "checked", null);
  __decorate([
    n4({ type: Boolean })
  ], Radio.prototype, "required", void 0);
  __decorate([
    n4()
  ], Radio.prototype, "value", void 0);
  __decorate([
    e4(".container")
  ], Radio.prototype, "container", void 0);

  // node_modules/@material/web/radio/internal/radio-styles.js
  var styles14 = i`@layer{:host{display:inline-flex;height:var(--md-radio-icon-size, 20px);outline:none;position:relative;vertical-align:top;width:var(--md-radio-icon-size, 20px);-webkit-tap-highlight-color:rgba(0,0,0,0);cursor:pointer;--md-ripple-hover-color: var(--md-radio-hover-state-layer-color, var(--md-sys-color-on-surface, #1d1b20));--md-ripple-hover-opacity: var(--md-radio-hover-state-layer-opacity, 0.08);--md-ripple-pressed-color: var(--md-radio-pressed-state-layer-color, var(--md-sys-color-primary, #6750a4));--md-ripple-pressed-opacity: var(--md-radio-pressed-state-layer-opacity, 0.12)}:host([disabled]){cursor:default}:host([touch-target=wrapper]){margin:max(0px,(48px - var(--md-radio-icon-size, 20px))/2)}.container{display:flex;height:100%;place-content:center;place-items:center;width:100%}md-focus-ring{height:44px;inset:unset;width:44px}.checked{--md-ripple-hover-color: var(--md-radio-selected-hover-state-layer-color, var(--md-sys-color-primary, #6750a4));--md-ripple-hover-opacity: var(--md-radio-selected-hover-state-layer-opacity, 0.08);--md-ripple-pressed-color: var(--md-radio-selected-pressed-state-layer-color, var(--md-sys-color-on-surface, #1d1b20));--md-ripple-pressed-opacity: var(--md-radio-selected-pressed-state-layer-opacity, 0.12)}input{appearance:none;height:48px;margin:0;position:absolute;width:48px;cursor:inherit}:host([touch-target=none]) input{width:100%;height:100%}md-ripple{border-radius:50%;height:var(--md-radio-state-layer-size, 40px);inset:unset;width:var(--md-radio-state-layer-size, 40px)}.icon{fill:var(--md-radio-icon-color, var(--md-sys-color-on-surface-variant, #49454f));inset:0;position:absolute}.outer.circle{transition:fill 50ms linear}.inner.circle{opacity:0;transform-origin:center;transition:opacity 50ms linear}.checked .icon{fill:var(--md-radio-selected-icon-color, var(--md-sys-color-primary, #6750a4))}.checked .inner.circle{animation:inner-circle-grow 300ms cubic-bezier(0.05, 0.7, 0.1, 1);opacity:1}@keyframes inner-circle-grow{from{transform:scale(0)}to{transform:scale(1)}}:host([disabled]) .circle{animation-duration:0s;transition-duration:0s}:host(:hover) .icon{fill:var(--md-radio-hover-icon-color, var(--md-sys-color-on-surface, #1d1b20))}:host(:focus-within) .icon{fill:var(--md-radio-focus-icon-color, var(--md-sys-color-on-surface, #1d1b20))}:host(:active) .icon{fill:var(--md-radio-pressed-icon-color, var(--md-sys-color-on-surface, #1d1b20))}:host([disabled]) .icon{fill:var(--md-radio-disabled-unselected-icon-color, var(--md-sys-color-on-surface, #1d1b20));opacity:var(--md-radio-disabled-unselected-icon-opacity, 0.38)}:host(:hover) .checked .icon{fill:var(--md-radio-selected-hover-icon-color, var(--md-sys-color-primary, #6750a4))}:host(:focus-within) .checked .icon{fill:var(--md-radio-selected-focus-icon-color, var(--md-sys-color-primary, #6750a4))}:host(:active) .checked .icon{fill:var(--md-radio-selected-pressed-icon-color, var(--md-sys-color-primary, #6750a4))}:host([disabled]) .checked .icon{fill:var(--md-radio-disabled-selected-icon-color, var(--md-sys-color-on-surface, #1d1b20));opacity:var(--md-radio-disabled-selected-icon-opacity, 0.38)}}@layer hcm{@media(forced-colors: active){.icon{fill:CanvasText}:host([disabled]) .icon{fill:GrayText;opacity:1}}}
`;

  // node_modules/@material/web/radio/radio.js
  var MdRadio = class MdRadio2 extends Radio {
  };
  MdRadio.styles = [styles14];
  MdRadio = __decorate([
    t2("md-radio")
  ], MdRadio);

  // node_modules/@material/web/field/internal/field.js
  var Field = class extends h3 {
    constructor() {
      super(...arguments);
      this.disabled = false;
      this.error = false;
      this.focused = false;
      this.label = "";
      this.noAsterisk = false;
      this.populated = false;
      this.required = false;
      this.resizable = false;
      this.supportingText = "";
      this.errorText = "";
      this.count = -1;
      this.max = -1;
      this.hasStart = false;
      this.hasEnd = false;
      this.isAnimating = false;
      this.refreshErrorAlert = false;
      this.disableTransitions = false;
    }
    get counterText() {
      const countAsNumber = this.count ?? -1;
      const maxAsNumber = this.max ?? -1;
      if (countAsNumber < 0 || maxAsNumber <= 0) {
        return "";
      }
      return `${countAsNumber} / ${maxAsNumber}`;
    }
    get supportingOrErrorText() {
      return this.error && this.errorText ? this.errorText : this.supportingText;
    }
    /**
     * Re-announces the field's error supporting text to screen readers.
     *
     * Error text announces to screen readers anytime it is visible and changes.
     * Use the method to re-announce the message when the text has not changed,
     * but announcement is still needed (such as for `reportValidity()`).
     */
    reannounceError() {
      this.refreshErrorAlert = true;
    }
    update(props) {
      const isDisabledChanging = props.has("disabled") && props.get("disabled") !== void 0;
      if (isDisabledChanging) {
        this.disableTransitions = true;
      }
      if (this.disabled && this.focused) {
        props.set("focused", true);
        this.focused = false;
      }
      this.animateLabelIfNeeded({
        wasFocused: props.get("focused"),
        wasPopulated: props.get("populated")
      });
      super.update(props);
    }
    render() {
      const floatingLabel = this.renderLabel(
        /*isFloating*/
        true
      );
      const restingLabel = this.renderLabel(
        /*isFloating*/
        false
      );
      const outline = this.renderOutline?.(floatingLabel);
      const classes = {
        "disabled": this.disabled,
        "disable-transitions": this.disableTransitions,
        "error": this.error && !this.disabled,
        "focused": this.focused,
        "with-start": this.hasStart,
        "with-end": this.hasEnd,
        "populated": this.populated,
        "resizable": this.resizable,
        "required": this.required,
        "no-label": !this.label
      };
      return ke`
      <div class="field ${Rt(classes)}">
        <div class="container-overflow">
          ${this.renderBackground?.()} ${this.renderIndicator?.()} ${outline}
          <div class="container">
            <div class="start">
              <slot name="start"></slot>
            </div>
            <div class="middle">
              <div class="label-wrapper">
                ${restingLabel} ${outline ? D : floatingLabel}
              </div>
              <div class="content">
                <slot></slot>
              </div>
            </div>
            <div class="end">
              <slot name="end"></slot>
            </div>
          </div>
        </div>
        ${this.renderSupportingText()}
      </div>
    `;
    }
    updated(changed) {
      if (changed.has("supportingText") || changed.has("errorText") || changed.has("count") || changed.has("max")) {
        this.updateSlottedAriaDescribedBy();
      }
      if (this.refreshErrorAlert) {
        requestAnimationFrame(() => {
          this.refreshErrorAlert = false;
        });
      }
      if (this.disableTransitions) {
        requestAnimationFrame(() => {
          this.disableTransitions = false;
        });
      }
    }
    renderSupportingText() {
      const { supportingOrErrorText, counterText } = this;
      if (!supportingOrErrorText && !counterText) {
        return D;
      }
      const start = ke`<span>${supportingOrErrorText}</span>`;
      const end = counterText ? ke`<span class="counter">${counterText}</span>` : D;
      const shouldErrorAnnounce = this.error && this.errorText && !this.refreshErrorAlert;
      const role = shouldErrorAnnounce ? "alert" : D;
      return ke`
      <div class="supporting-text" role=${role}>${start}${end}</div>
      <slot
        name="aria-describedby"
        @slotchange=${this.updateSlottedAriaDescribedBy}></slot>
    `;
    }
    updateSlottedAriaDescribedBy() {
      for (const element of this.slottedAriaDescribedBy) {
        Q(ke`${this.supportingOrErrorText} ${this.counterText}`, element);
        element.setAttribute("hidden", "");
      }
    }
    renderLabel(isFloating) {
      if (!this.label) {
        return D;
      }
      let visible;
      if (isFloating) {
        visible = this.focused || this.populated || this.isAnimating;
      } else {
        visible = !this.focused && !this.populated && !this.isAnimating;
      }
      const classes = {
        "hidden": !visible,
        "floating": isFloating,
        "resting": !isFloating
      };
      const labelText = `${this.label}${this.required && !this.noAsterisk ? "*" : ""}`;
      return ke`
      <span class="label ${Rt(classes)}" aria-hidden=${!visible}
        >${labelText}</span
      >
    `;
    }
    animateLabelIfNeeded({ wasFocused, wasPopulated }) {
      if (!this.label) {
        return;
      }
      wasFocused ??= this.focused;
      wasPopulated ??= this.populated;
      const wasFloating = wasFocused || wasPopulated;
      const shouldBeFloating = this.focused || this.populated;
      if (wasFloating === shouldBeFloating) {
        return;
      }
      this.isAnimating = true;
      this.labelAnimation?.cancel();
      this.labelAnimation = this.floatingLabelEl?.animate(this.getLabelKeyframes(), { duration: 150, easing: EASING.STANDARD });
      this.labelAnimation?.addEventListener("finish", () => {
        this.isAnimating = false;
      });
    }
    getLabelKeyframes() {
      const { floatingLabelEl, restingLabelEl } = this;
      if (!floatingLabelEl || !restingLabelEl) {
        return [];
      }
      const { x: floatingX, y: floatingY, height: floatingHeight } = floatingLabelEl.getBoundingClientRect();
      const { x: restingX, y: restingY, height: restingHeight } = restingLabelEl.getBoundingClientRect();
      const floatingScrollWidth = floatingLabelEl.scrollWidth;
      const restingScrollWidth = restingLabelEl.scrollWidth;
      const scale = restingScrollWidth / floatingScrollWidth;
      const xDelta = restingX - floatingX;
      const yDelta = restingY - floatingY + Math.round((restingHeight - floatingHeight * scale) / 2);
      const restTransform = `translateX(${xDelta}px) translateY(${yDelta}px) scale(${scale})`;
      const floatTransform = `translateX(0) translateY(0) scale(1)`;
      const restingClientWidth = restingLabelEl.clientWidth;
      const isRestingClipped = restingScrollWidth > restingClientWidth;
      const width = isRestingClipped ? `${restingClientWidth / scale}px` : "";
      if (this.focused || this.populated) {
        return [
          { transform: restTransform, width },
          { transform: floatTransform, width }
        ];
      }
      return [
        { transform: floatTransform, width },
        { transform: restTransform, width }
      ];
    }
    getSurfacePositionClientRect() {
      return this.containerEl.getBoundingClientRect();
    }
  };
  __decorate([
    n4({ type: Boolean })
  ], Field.prototype, "disabled", void 0);
  __decorate([
    n4({ type: Boolean })
  ], Field.prototype, "error", void 0);
  __decorate([
    n4({ type: Boolean })
  ], Field.prototype, "focused", void 0);
  __decorate([
    n4()
  ], Field.prototype, "label", void 0);
  __decorate([
    n4({ type: Boolean, attribute: "no-asterisk" })
  ], Field.prototype, "noAsterisk", void 0);
  __decorate([
    n4({ type: Boolean })
  ], Field.prototype, "populated", void 0);
  __decorate([
    n4({ type: Boolean })
  ], Field.prototype, "required", void 0);
  __decorate([
    n4({ type: Boolean })
  ], Field.prototype, "resizable", void 0);
  __decorate([
    n4({ attribute: "supporting-text" })
  ], Field.prototype, "supportingText", void 0);
  __decorate([
    n4({ attribute: "error-text" })
  ], Field.prototype, "errorText", void 0);
  __decorate([
    n4({ type: Number })
  ], Field.prototype, "count", void 0);
  __decorate([
    n4({ type: Number })
  ], Field.prototype, "max", void 0);
  __decorate([
    n4({ type: Boolean, attribute: "has-start" })
  ], Field.prototype, "hasStart", void 0);
  __decorate([
    n4({ type: Boolean, attribute: "has-end" })
  ], Field.prototype, "hasEnd", void 0);
  __decorate([
    o4({ slot: "aria-describedby" })
  ], Field.prototype, "slottedAriaDescribedBy", void 0);
  __decorate([
    r4()
  ], Field.prototype, "isAnimating", void 0);
  __decorate([
    r4()
  ], Field.prototype, "refreshErrorAlert", void 0);
  __decorate([
    r4()
  ], Field.prototype, "disableTransitions", void 0);
  __decorate([
    e4(".label.floating")
  ], Field.prototype, "floatingLabelEl", void 0);
  __decorate([
    e4(".label.resting")
  ], Field.prototype, "restingLabelEl", void 0);
  __decorate([
    e4(".container")
  ], Field.prototype, "containerEl", void 0);

  // node_modules/@material/web/field/internal/outlined-field.js
  var OutlinedField = class extends Field {
    renderOutline(floatingLabel) {
      return ke`
      <div class="outline">
        <div class="outline-start"></div>
        <div class="outline-notch">
          <div class="outline-panel-inactive"></div>
          <div class="outline-panel-active"></div>
          <div class="outline-label">${floatingLabel}</div>
        </div>
        <div class="outline-end"></div>
      </div>
    `;
    }
  };

  // node_modules/@material/web/field/internal/outlined-styles.js
  var styles15 = i`@layer styles{:host{--_bottom-space: var(--md-outlined-field-bottom-space, 16px);--_content-color: var(--md-outlined-field-content-color, var(--md-sys-color-on-surface, #1d1b20));--_content-font: var(--md-outlined-field-content-font, var(--md-sys-typescale-body-large-font, var(--md-ref-typeface-plain, Roboto)));--_content-line-height: var(--md-outlined-field-content-line-height, var(--md-sys-typescale-body-large-line-height, 1.5rem));--_content-size: var(--md-outlined-field-content-size, var(--md-sys-typescale-body-large-size, 1rem));--_content-space: var(--md-outlined-field-content-space, 16px);--_content-weight: var(--md-outlined-field-content-weight, var(--md-sys-typescale-body-large-weight, var(--md-ref-typeface-weight-regular, 400)));--_disabled-content-color: var(--md-outlined-field-disabled-content-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-content-opacity: var(--md-outlined-field-disabled-content-opacity, 0.38);--_disabled-label-text-color: var(--md-outlined-field-disabled-label-text-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-label-text-opacity: var(--md-outlined-field-disabled-label-text-opacity, 0.38);--_disabled-leading-content-color: var(--md-outlined-field-disabled-leading-content-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-leading-content-opacity: var(--md-outlined-field-disabled-leading-content-opacity, 0.38);--_disabled-outline-color: var(--md-outlined-field-disabled-outline-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-outline-opacity: var(--md-outlined-field-disabled-outline-opacity, 0.12);--_disabled-outline-width: var(--md-outlined-field-disabled-outline-width, 1px);--_disabled-supporting-text-color: var(--md-outlined-field-disabled-supporting-text-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-supporting-text-opacity: var(--md-outlined-field-disabled-supporting-text-opacity, 0.38);--_disabled-trailing-content-color: var(--md-outlined-field-disabled-trailing-content-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-trailing-content-opacity: var(--md-outlined-field-disabled-trailing-content-opacity, 0.38);--_error-content-color: var(--md-outlined-field-error-content-color, var(--md-sys-color-on-surface, #1d1b20));--_error-focus-content-color: var(--md-outlined-field-error-focus-content-color, var(--md-sys-color-on-surface, #1d1b20));--_error-focus-label-text-color: var(--md-outlined-field-error-focus-label-text-color, var(--md-sys-color-error, #b3261e));--_error-focus-leading-content-color: var(--md-outlined-field-error-focus-leading-content-color, var(--md-sys-color-on-surface-variant, #49454f));--_error-focus-outline-color: var(--md-outlined-field-error-focus-outline-color, var(--md-sys-color-error, #b3261e));--_error-focus-supporting-text-color: var(--md-outlined-field-error-focus-supporting-text-color, var(--md-sys-color-error, #b3261e));--_error-focus-trailing-content-color: var(--md-outlined-field-error-focus-trailing-content-color, var(--md-sys-color-error, #b3261e));--_error-hover-content-color: var(--md-outlined-field-error-hover-content-color, var(--md-sys-color-on-surface, #1d1b20));--_error-hover-label-text-color: var(--md-outlined-field-error-hover-label-text-color, var(--md-sys-color-on-error-container, #410e0b));--_error-hover-leading-content-color: var(--md-outlined-field-error-hover-leading-content-color, var(--md-sys-color-on-surface-variant, #49454f));--_error-hover-outline-color: var(--md-outlined-field-error-hover-outline-color, var(--md-sys-color-on-error-container, #410e0b));--_error-hover-supporting-text-color: var(--md-outlined-field-error-hover-supporting-text-color, var(--md-sys-color-error, #b3261e));--_error-hover-trailing-content-color: var(--md-outlined-field-error-hover-trailing-content-color, var(--md-sys-color-on-error-container, #410e0b));--_error-label-text-color: var(--md-outlined-field-error-label-text-color, var(--md-sys-color-error, #b3261e));--_error-leading-content-color: var(--md-outlined-field-error-leading-content-color, var(--md-sys-color-on-surface-variant, #49454f));--_error-outline-color: var(--md-outlined-field-error-outline-color, var(--md-sys-color-error, #b3261e));--_error-supporting-text-color: var(--md-outlined-field-error-supporting-text-color, var(--md-sys-color-error, #b3261e));--_error-trailing-content-color: var(--md-outlined-field-error-trailing-content-color, var(--md-sys-color-error, #b3261e));--_focus-content-color: var(--md-outlined-field-focus-content-color, var(--md-sys-color-on-surface, #1d1b20));--_focus-label-text-color: var(--md-outlined-field-focus-label-text-color, var(--md-sys-color-primary, #6750a4));--_focus-leading-content-color: var(--md-outlined-field-focus-leading-content-color, var(--md-sys-color-on-surface-variant, #49454f));--_focus-outline-color: var(--md-outlined-field-focus-outline-color, var(--md-sys-color-primary, #6750a4));--_focus-outline-width: var(--md-outlined-field-focus-outline-width, 3px);--_focus-supporting-text-color: var(--md-outlined-field-focus-supporting-text-color, var(--md-sys-color-on-surface-variant, #49454f));--_focus-trailing-content-color: var(--md-outlined-field-focus-trailing-content-color, var(--md-sys-color-on-surface-variant, #49454f));--_hover-content-color: var(--md-outlined-field-hover-content-color, var(--md-sys-color-on-surface, #1d1b20));--_hover-label-text-color: var(--md-outlined-field-hover-label-text-color, var(--md-sys-color-on-surface, #1d1b20));--_hover-leading-content-color: var(--md-outlined-field-hover-leading-content-color, var(--md-sys-color-on-surface-variant, #49454f));--_hover-outline-color: var(--md-outlined-field-hover-outline-color, var(--md-sys-color-on-surface, #1d1b20));--_hover-outline-width: var(--md-outlined-field-hover-outline-width, 1px);--_hover-supporting-text-color: var(--md-outlined-field-hover-supporting-text-color, var(--md-sys-color-on-surface-variant, #49454f));--_hover-trailing-content-color: var(--md-outlined-field-hover-trailing-content-color, var(--md-sys-color-on-surface-variant, #49454f));--_label-text-color: var(--md-outlined-field-label-text-color, var(--md-sys-color-on-surface-variant, #49454f));--_label-text-font: var(--md-outlined-field-label-text-font, var(--md-sys-typescale-body-large-font, var(--md-ref-typeface-plain, Roboto)));--_label-text-line-height: var(--md-outlined-field-label-text-line-height, var(--md-sys-typescale-body-large-line-height, 1.5rem));--_label-text-padding-bottom: var(--md-outlined-field-label-text-padding-bottom, 8px);--_label-text-populated-line-height: var(--md-outlined-field-label-text-populated-line-height, var(--md-sys-typescale-body-small-line-height, 1rem));--_label-text-populated-size: var(--md-outlined-field-label-text-populated-size, var(--md-sys-typescale-body-small-size, 0.75rem));--_label-text-size: var(--md-outlined-field-label-text-size, var(--md-sys-typescale-body-large-size, 1rem));--_label-text-weight: var(--md-outlined-field-label-text-weight, var(--md-sys-typescale-body-large-weight, var(--md-ref-typeface-weight-regular, 400)));--_leading-content-color: var(--md-outlined-field-leading-content-color, var(--md-sys-color-on-surface-variant, #49454f));--_leading-space: var(--md-outlined-field-leading-space, 16px);--_outline-color: var(--md-outlined-field-outline-color, var(--md-sys-color-outline, #79747e));--_outline-label-padding: var(--md-outlined-field-outline-label-padding, 4px);--_outline-width: var(--md-outlined-field-outline-width, 1px);--_supporting-text-color: var(--md-outlined-field-supporting-text-color, var(--md-sys-color-on-surface-variant, #49454f));--_supporting-text-font: var(--md-outlined-field-supporting-text-font, var(--md-sys-typescale-body-small-font, var(--md-ref-typeface-plain, Roboto)));--_supporting-text-leading-space: var(--md-outlined-field-supporting-text-leading-space, 16px);--_supporting-text-line-height: var(--md-outlined-field-supporting-text-line-height, var(--md-sys-typescale-body-small-line-height, 1rem));--_supporting-text-size: var(--md-outlined-field-supporting-text-size, var(--md-sys-typescale-body-small-size, 0.75rem));--_supporting-text-top-space: var(--md-outlined-field-supporting-text-top-space, 4px);--_supporting-text-trailing-space: var(--md-outlined-field-supporting-text-trailing-space, 16px);--_supporting-text-weight: var(--md-outlined-field-supporting-text-weight, var(--md-sys-typescale-body-small-weight, var(--md-ref-typeface-weight-regular, 400)));--_top-space: var(--md-outlined-field-top-space, 16px);--_trailing-content-color: var(--md-outlined-field-trailing-content-color, var(--md-sys-color-on-surface-variant, #49454f));--_trailing-space: var(--md-outlined-field-trailing-space, 16px);--_with-leading-content-leading-space: var(--md-outlined-field-with-leading-content-leading-space, 12px);--_with-trailing-content-trailing-space: var(--md-outlined-field-with-trailing-content-trailing-space, 12px);--_container-shape-start-start: var(--md-outlined-field-container-shape-start-start, var(--md-outlined-field-container-shape, var(--md-sys-shape-corner-extra-small, 4px)));--_container-shape-start-end: var(--md-outlined-field-container-shape-start-end, var(--md-outlined-field-container-shape, var(--md-sys-shape-corner-extra-small, 4px)));--_container-shape-end-end: var(--md-outlined-field-container-shape-end-end, var(--md-outlined-field-container-shape, var(--md-sys-shape-corner-extra-small, 4px)));--_container-shape-end-start: var(--md-outlined-field-container-shape-end-start, var(--md-outlined-field-container-shape, var(--md-sys-shape-corner-extra-small, 4px)))}.outline{border-color:var(--_outline-color);border-radius:inherit;display:flex;pointer-events:none;height:100%;position:absolute;width:100%;z-index:1}.outline-start::before,.outline-start::after,.outline-panel-inactive::before,.outline-panel-inactive::after,.outline-panel-active::before,.outline-panel-active::after,.outline-end::before,.outline-end::after{border:inherit;content:"";inset:0;position:absolute}.outline-start,.outline-end{border:inherit;border-radius:inherit;box-sizing:border-box;position:relative}.outline-start::before,.outline-start::after,.outline-end::before,.outline-end::after{border-bottom-style:solid;border-top-style:solid}.outline-start::after,.outline-end::after{opacity:0;transition:opacity 150ms cubic-bezier(0.2, 0, 0, 1)}.focused .outline-start::after,.focused .outline-end::after{opacity:1}.outline-start::before,.outline-start::after{border-inline-start-style:solid;border-inline-end-style:none;border-start-start-radius:inherit;border-start-end-radius:0;border-end-start-radius:inherit;border-end-end-radius:0;margin-inline-end:var(--_outline-label-padding)}.outline-end{flex-grow:1;margin-inline-start:calc(-1*var(--_outline-label-padding))}.outline-end::before,.outline-end::after{border-inline-start-style:none;border-inline-end-style:solid;border-start-start-radius:0;border-start-end-radius:inherit;border-end-start-radius:0;border-end-end-radius:inherit}.outline-notch{align-items:flex-start;border:inherit;display:flex;margin-inline-start:calc(-1*var(--_outline-label-padding));margin-inline-end:var(--_outline-label-padding);max-width:calc(100% - var(--_leading-space) - var(--_trailing-space));padding:0 var(--_outline-label-padding);position:relative}.no-label .outline-notch{display:none}.outline-panel-inactive,.outline-panel-active{border:inherit;border-bottom-style:solid;inset:0;position:absolute}.outline-panel-inactive::before,.outline-panel-inactive::after,.outline-panel-active::before,.outline-panel-active::after{border-top-style:solid;border-bottom:none;bottom:auto;transform:scaleX(1);transition:transform 150ms cubic-bezier(0.2, 0, 0, 1)}.outline-panel-inactive::before,.outline-panel-active::before{right:50%;transform-origin:top left}.outline-panel-inactive::after,.outline-panel-active::after{left:50%;transform-origin:top right}.populated .outline-panel-inactive::before,.populated .outline-panel-inactive::after,.populated .outline-panel-active::before,.populated .outline-panel-active::after,.focused .outline-panel-inactive::before,.focused .outline-panel-inactive::after,.focused .outline-panel-active::before,.focused .outline-panel-active::after{transform:scaleX(0)}.outline-panel-active{opacity:0;transition:opacity 150ms cubic-bezier(0.2, 0, 0, 1)}.focused .outline-panel-active{opacity:1}.outline-label{display:flex;max-width:100%;transform:translateY(calc(-100% + var(--_label-text-padding-bottom)))}.outline-start,.field:not(.with-start) .content ::slotted(*){padding-inline-start:max(var(--_leading-space),max(var(--_container-shape-start-start),var(--_container-shape-end-start)) + var(--_outline-label-padding))}.field:not(.with-start) .label-wrapper{margin-inline-start:max(var(--_leading-space),max(var(--_container-shape-start-start),var(--_container-shape-end-start)) + var(--_outline-label-padding))}.field:not(.with-end) .content ::slotted(*){padding-inline-end:max(var(--_trailing-space),max(var(--_container-shape-start-end),var(--_container-shape-end-end)))}.field:not(.with-end) .label-wrapper{margin-inline-end:max(var(--_trailing-space),max(var(--_container-shape-start-end),var(--_container-shape-end-end)))}.outline-start::before,.outline-end::before,.outline-panel-inactive,.outline-panel-inactive::before,.outline-panel-inactive::after{border-width:var(--_outline-width)}:hover .outline{border-color:var(--_hover-outline-color);color:var(--_hover-outline-color)}:hover .outline-start::before,:hover .outline-end::before,:hover .outline-panel-inactive,:hover .outline-panel-inactive::before,:hover .outline-panel-inactive::after{border-width:var(--_hover-outline-width)}.focused .outline{border-color:var(--_focus-outline-color);color:var(--_focus-outline-color)}.outline-start::after,.outline-end::after,.outline-panel-active,.outline-panel-active::before,.outline-panel-active::after{border-width:var(--_focus-outline-width)}.disabled .outline{border-color:var(--_disabled-outline-color);color:var(--_disabled-outline-color)}.disabled .outline-start,.disabled .outline-end,.disabled .outline-panel-inactive{opacity:var(--_disabled-outline-opacity)}.disabled .outline-start::before,.disabled .outline-end::before,.disabled .outline-panel-inactive,.disabled .outline-panel-inactive::before,.disabled .outline-panel-inactive::after{border-width:var(--_disabled-outline-width)}.error .outline{border-color:var(--_error-outline-color);color:var(--_error-outline-color)}.error:hover .outline{border-color:var(--_error-hover-outline-color);color:var(--_error-hover-outline-color)}.error.focused .outline{border-color:var(--_error-focus-outline-color);color:var(--_error-focus-outline-color)}.resizable .container{bottom:var(--_focus-outline-width);inset-inline-end:var(--_focus-outline-width);clip-path:inset(var(--_focus-outline-width) 0 0 var(--_focus-outline-width))}.resizable .container>*{top:var(--_focus-outline-width);inset-inline-start:var(--_focus-outline-width)}.resizable .container:dir(rtl){clip-path:inset(var(--_focus-outline-width) var(--_focus-outline-width) 0 0)}}@layer hcm{@media(forced-colors: active){.disabled .outline{border-color:GrayText;color:GrayText}.disabled :is(.outline-start,.outline-end,.outline-panel-inactive){opacity:1}}}
`;

  // node_modules/@material/web/field/internal/shared-styles.js
  var styles16 = i`:host{display:inline-flex;resize:both}.field{display:flex;flex:1;flex-direction:column;writing-mode:horizontal-tb;max-width:100%}.container-overflow{border-start-start-radius:var(--_container-shape-start-start);border-start-end-radius:var(--_container-shape-start-end);border-end-end-radius:var(--_container-shape-end-end);border-end-start-radius:var(--_container-shape-end-start);display:flex;height:100%;position:relative}.container{align-items:center;border-radius:inherit;display:flex;flex:1;max-height:100%;min-height:100%;min-width:min-content;position:relative}.field,.container-overflow{resize:inherit}.resizable:not(.disabled) .container{resize:inherit;overflow:hidden}.disabled{pointer-events:none}@layer styles{.start,.middle,.end{display:flex;box-sizing:border-box;height:100%;position:relative}.start{color:var(--_leading-content-color)}.end{color:var(--_trailing-content-color)}.start,.end{align-items:center;justify-content:center}.with-start .start{margin-inline:var(--_with-leading-content-leading-space) var(--_content-space)}.with-end .end{margin-inline:var(--_content-space) var(--_with-trailing-content-trailing-space)}.middle{align-items:stretch;align-self:baseline;flex:1}.content{color:var(--_content-color);display:flex;flex:1;opacity:0;transition:opacity 83ms cubic-bezier(0.2, 0, 0, 1)}.no-label .content,.focused .content,.populated .content{opacity:1;transition-delay:67ms}:is(.disabled,.disable-transitions) .content{transition:none}.content ::slotted(*){all:unset;color:currentColor;font-family:var(--_content-font);font-size:var(--_content-size);line-height:var(--_content-line-height);font-weight:var(--_content-weight);width:100%;overflow-wrap:revert;white-space:revert}.content ::slotted(:not(textarea)){padding-top:var(--_top-space);padding-bottom:var(--_bottom-space)}.content ::slotted(textarea){margin-top:var(--_top-space);margin-bottom:var(--_bottom-space)}:hover .content{color:var(--_hover-content-color)}:hover .start{color:var(--_hover-leading-content-color)}:hover .end{color:var(--_hover-trailing-content-color)}.focused .content{color:var(--_focus-content-color)}.focused .start{color:var(--_focus-leading-content-color)}.focused .end{color:var(--_focus-trailing-content-color)}.disabled .content{color:var(--_disabled-content-color)}.disabled.no-label .content,.disabled.focused .content,.disabled.populated .content{opacity:var(--_disabled-content-opacity)}.disabled .start{color:var(--_disabled-leading-content-color);opacity:var(--_disabled-leading-content-opacity)}.disabled .end{color:var(--_disabled-trailing-content-color);opacity:var(--_disabled-trailing-content-opacity)}.error .content{color:var(--_error-content-color)}.error .start{color:var(--_error-leading-content-color)}.error .end{color:var(--_error-trailing-content-color)}.error:hover .content{color:var(--_error-hover-content-color)}.error:hover .start{color:var(--_error-hover-leading-content-color)}.error:hover .end{color:var(--_error-hover-trailing-content-color)}.error.focused .content{color:var(--_error-focus-content-color)}.error.focused .start{color:var(--_error-focus-leading-content-color)}.error.focused .end{color:var(--_error-focus-trailing-content-color)}}@layer hcm{@media(forced-colors: active){.disabled :is(.start,.content,.end){color:GrayText;opacity:1}}}@layer styles{.label{box-sizing:border-box;color:var(--_label-text-color);overflow:hidden;max-width:100%;text-overflow:ellipsis;white-space:nowrap;z-index:1;font-family:var(--_label-text-font);font-size:var(--_label-text-size);line-height:var(--_label-text-line-height);font-weight:var(--_label-text-weight);width:min-content}.label-wrapper{inset:0;pointer-events:none;position:absolute}.label.resting{position:absolute;top:var(--_top-space)}.label.floating{font-size:var(--_label-text-populated-size);line-height:var(--_label-text-populated-line-height);transform-origin:top left}.label.hidden{opacity:0}.no-label .label{display:none}.label-wrapper{inset:0;position:absolute;text-align:initial}:hover .label{color:var(--_hover-label-text-color)}.focused .label{color:var(--_focus-label-text-color)}.disabled .label{color:var(--_disabled-label-text-color)}.disabled .label:not(.hidden){opacity:var(--_disabled-label-text-opacity)}.error .label{color:var(--_error-label-text-color)}.error:hover .label{color:var(--_error-hover-label-text-color)}.error.focused .label{color:var(--_error-focus-label-text-color)}}@layer hcm{@media(forced-colors: active){.disabled .label:not(.hidden){color:GrayText;opacity:1}}}@layer styles{.supporting-text{color:var(--_supporting-text-color);display:flex;font-family:var(--_supporting-text-font);font-size:var(--_supporting-text-size);line-height:var(--_supporting-text-line-height);font-weight:var(--_supporting-text-weight);gap:16px;justify-content:space-between;padding-inline-start:var(--_supporting-text-leading-space);padding-inline-end:var(--_supporting-text-trailing-space);padding-top:var(--_supporting-text-top-space)}.supporting-text :nth-child(2){flex-shrink:0}:hover .supporting-text{color:var(--_hover-supporting-text-color)}.focus .supporting-text{color:var(--_focus-supporting-text-color)}.disabled .supporting-text{color:var(--_disabled-supporting-text-color);opacity:var(--_disabled-supporting-text-opacity)}.error .supporting-text{color:var(--_error-supporting-text-color)}.error:hover .supporting-text{color:var(--_error-hover-supporting-text-color)}.error.focus .supporting-text{color:var(--_error-focus-supporting-text-color)}}@layer hcm{@media(forced-colors: active){.disabled .supporting-text{color:GrayText;opacity:1}}}
`;

  // node_modules/@material/web/field/outlined-field.js
  var MdOutlinedField = class MdOutlinedField2 extends OutlinedField {
  };
  MdOutlinedField.styles = [styles16, styles15];
  MdOutlinedField = __decorate([
    t2("md-outlined-field")
  ], MdOutlinedField);

  // node_modules/@material/web/textfield/internal/outlined-styles.js
  var styles17 = i`:host{--_caret-color: var(--md-outlined-text-field-caret-color, var(--md-sys-color-primary, #6750a4));--_disabled-input-text-color: var(--md-outlined-text-field-disabled-input-text-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-input-text-opacity: var(--md-outlined-text-field-disabled-input-text-opacity, 0.38);--_disabled-label-text-color: var(--md-outlined-text-field-disabled-label-text-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-label-text-opacity: var(--md-outlined-text-field-disabled-label-text-opacity, 0.38);--_disabled-leading-icon-color: var(--md-outlined-text-field-disabled-leading-icon-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-leading-icon-opacity: var(--md-outlined-text-field-disabled-leading-icon-opacity, 0.38);--_disabled-outline-color: var(--md-outlined-text-field-disabled-outline-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-outline-opacity: var(--md-outlined-text-field-disabled-outline-opacity, 0.12);--_disabled-outline-width: var(--md-outlined-text-field-disabled-outline-width, 1px);--_disabled-supporting-text-color: var(--md-outlined-text-field-disabled-supporting-text-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-supporting-text-opacity: var(--md-outlined-text-field-disabled-supporting-text-opacity, 0.38);--_disabled-trailing-icon-color: var(--md-outlined-text-field-disabled-trailing-icon-color, var(--md-sys-color-on-surface, #1d1b20));--_disabled-trailing-icon-opacity: var(--md-outlined-text-field-disabled-trailing-icon-opacity, 0.38);--_error-focus-caret-color: var(--md-outlined-text-field-error-focus-caret-color, var(--md-sys-color-error, #b3261e));--_error-focus-input-text-color: var(--md-outlined-text-field-error-focus-input-text-color, var(--md-sys-color-on-surface, #1d1b20));--_error-focus-label-text-color: var(--md-outlined-text-field-error-focus-label-text-color, var(--md-sys-color-error, #b3261e));--_error-focus-leading-icon-color: var(--md-outlined-text-field-error-focus-leading-icon-color, var(--md-sys-color-on-surface-variant, #49454f));--_error-focus-outline-color: var(--md-outlined-text-field-error-focus-outline-color, var(--md-sys-color-error, #b3261e));--_error-focus-supporting-text-color: var(--md-outlined-text-field-error-focus-supporting-text-color, var(--md-sys-color-error, #b3261e));--_error-focus-trailing-icon-color: var(--md-outlined-text-field-error-focus-trailing-icon-color, var(--md-sys-color-error, #b3261e));--_error-hover-input-text-color: var(--md-outlined-text-field-error-hover-input-text-color, var(--md-sys-color-on-surface, #1d1b20));--_error-hover-label-text-color: var(--md-outlined-text-field-error-hover-label-text-color, var(--md-sys-color-on-error-container, #410e0b));--_error-hover-leading-icon-color: var(--md-outlined-text-field-error-hover-leading-icon-color, var(--md-sys-color-on-surface-variant, #49454f));--_error-hover-outline-color: var(--md-outlined-text-field-error-hover-outline-color, var(--md-sys-color-on-error-container, #410e0b));--_error-hover-supporting-text-color: var(--md-outlined-text-field-error-hover-supporting-text-color, var(--md-sys-color-error, #b3261e));--_error-hover-trailing-icon-color: var(--md-outlined-text-field-error-hover-trailing-icon-color, var(--md-sys-color-on-error-container, #410e0b));--_error-input-text-color: var(--md-outlined-text-field-error-input-text-color, var(--md-sys-color-on-surface, #1d1b20));--_error-label-text-color: var(--md-outlined-text-field-error-label-text-color, var(--md-sys-color-error, #b3261e));--_error-leading-icon-color: var(--md-outlined-text-field-error-leading-icon-color, var(--md-sys-color-on-surface-variant, #49454f));--_error-outline-color: var(--md-outlined-text-field-error-outline-color, var(--md-sys-color-error, #b3261e));--_error-supporting-text-color: var(--md-outlined-text-field-error-supporting-text-color, var(--md-sys-color-error, #b3261e));--_error-trailing-icon-color: var(--md-outlined-text-field-error-trailing-icon-color, var(--md-sys-color-error, #b3261e));--_focus-input-text-color: var(--md-outlined-text-field-focus-input-text-color, var(--md-sys-color-on-surface, #1d1b20));--_focus-label-text-color: var(--md-outlined-text-field-focus-label-text-color, var(--md-sys-color-primary, #6750a4));--_focus-leading-icon-color: var(--md-outlined-text-field-focus-leading-icon-color, var(--md-sys-color-on-surface-variant, #49454f));--_focus-outline-color: var(--md-outlined-text-field-focus-outline-color, var(--md-sys-color-primary, #6750a4));--_focus-outline-width: var(--md-outlined-text-field-focus-outline-width, 3px);--_focus-supporting-text-color: var(--md-outlined-text-field-focus-supporting-text-color, var(--md-sys-color-on-surface-variant, #49454f));--_focus-trailing-icon-color: var(--md-outlined-text-field-focus-trailing-icon-color, var(--md-sys-color-on-surface-variant, #49454f));--_hover-input-text-color: var(--md-outlined-text-field-hover-input-text-color, var(--md-sys-color-on-surface, #1d1b20));--_hover-label-text-color: var(--md-outlined-text-field-hover-label-text-color, var(--md-sys-color-on-surface, #1d1b20));--_hover-leading-icon-color: var(--md-outlined-text-field-hover-leading-icon-color, var(--md-sys-color-on-surface-variant, #49454f));--_hover-outline-color: var(--md-outlined-text-field-hover-outline-color, var(--md-sys-color-on-surface, #1d1b20));--_hover-outline-width: var(--md-outlined-text-field-hover-outline-width, 1px);--_hover-supporting-text-color: var(--md-outlined-text-field-hover-supporting-text-color, var(--md-sys-color-on-surface-variant, #49454f));--_hover-trailing-icon-color: var(--md-outlined-text-field-hover-trailing-icon-color, var(--md-sys-color-on-surface-variant, #49454f));--_input-text-color: var(--md-outlined-text-field-input-text-color, var(--md-sys-color-on-surface, #1d1b20));--_input-text-font: var(--md-outlined-text-field-input-text-font, var(--md-sys-typescale-body-large-font, var(--md-ref-typeface-plain, Roboto)));--_input-text-line-height: var(--md-outlined-text-field-input-text-line-height, var(--md-sys-typescale-body-large-line-height, 1.5rem));--_input-text-placeholder-color: var(--md-outlined-text-field-input-text-placeholder-color, var(--md-sys-color-on-surface-variant, #49454f));--_input-text-prefix-color: var(--md-outlined-text-field-input-text-prefix-color, var(--md-sys-color-on-surface-variant, #49454f));--_input-text-size: var(--md-outlined-text-field-input-text-size, var(--md-sys-typescale-body-large-size, 1rem));--_input-text-suffix-color: var(--md-outlined-text-field-input-text-suffix-color, var(--md-sys-color-on-surface-variant, #49454f));--_input-text-weight: var(--md-outlined-text-field-input-text-weight, var(--md-sys-typescale-body-large-weight, var(--md-ref-typeface-weight-regular, 400)));--_label-text-color: var(--md-outlined-text-field-label-text-color, var(--md-sys-color-on-surface-variant, #49454f));--_label-text-font: var(--md-outlined-text-field-label-text-font, var(--md-sys-typescale-body-large-font, var(--md-ref-typeface-plain, Roboto)));--_label-text-line-height: var(--md-outlined-text-field-label-text-line-height, var(--md-sys-typescale-body-large-line-height, 1.5rem));--_label-text-populated-line-height: var(--md-outlined-text-field-label-text-populated-line-height, var(--md-sys-typescale-body-small-line-height, 1rem));--_label-text-populated-size: var(--md-outlined-text-field-label-text-populated-size, var(--md-sys-typescale-body-small-size, 0.75rem));--_label-text-size: var(--md-outlined-text-field-label-text-size, var(--md-sys-typescale-body-large-size, 1rem));--_label-text-weight: var(--md-outlined-text-field-label-text-weight, var(--md-sys-typescale-body-large-weight, var(--md-ref-typeface-weight-regular, 400)));--_leading-icon-color: var(--md-outlined-text-field-leading-icon-color, var(--md-sys-color-on-surface-variant, #49454f));--_leading-icon-size: var(--md-outlined-text-field-leading-icon-size, 24px);--_outline-color: var(--md-outlined-text-field-outline-color, var(--md-sys-color-outline, #79747e));--_outline-width: var(--md-outlined-text-field-outline-width, 1px);--_supporting-text-color: var(--md-outlined-text-field-supporting-text-color, var(--md-sys-color-on-surface-variant, #49454f));--_supporting-text-font: var(--md-outlined-text-field-supporting-text-font, var(--md-sys-typescale-body-small-font, var(--md-ref-typeface-plain, Roboto)));--_supporting-text-line-height: var(--md-outlined-text-field-supporting-text-line-height, var(--md-sys-typescale-body-small-line-height, 1rem));--_supporting-text-size: var(--md-outlined-text-field-supporting-text-size, var(--md-sys-typescale-body-small-size, 0.75rem));--_supporting-text-weight: var(--md-outlined-text-field-supporting-text-weight, var(--md-sys-typescale-body-small-weight, var(--md-ref-typeface-weight-regular, 400)));--_trailing-icon-color: var(--md-outlined-text-field-trailing-icon-color, var(--md-sys-color-on-surface-variant, #49454f));--_trailing-icon-size: var(--md-outlined-text-field-trailing-icon-size, 24px);--_container-shape-start-start: var(--md-outlined-text-field-container-shape-start-start, var(--md-outlined-text-field-container-shape, var(--md-sys-shape-corner-extra-small, 4px)));--_container-shape-start-end: var(--md-outlined-text-field-container-shape-start-end, var(--md-outlined-text-field-container-shape, var(--md-sys-shape-corner-extra-small, 4px)));--_container-shape-end-end: var(--md-outlined-text-field-container-shape-end-end, var(--md-outlined-text-field-container-shape, var(--md-sys-shape-corner-extra-small, 4px)));--_container-shape-end-start: var(--md-outlined-text-field-container-shape-end-start, var(--md-outlined-text-field-container-shape, var(--md-sys-shape-corner-extra-small, 4px)));--_icon-input-space: var(--md-outlined-text-field-icon-input-space, 16px);--_leading-space: var(--md-outlined-text-field-leading-space, 16px);--_trailing-space: var(--md-outlined-text-field-trailing-space, 16px);--_top-space: var(--md-outlined-text-field-top-space, 16px);--_bottom-space: var(--md-outlined-text-field-bottom-space, 16px);--_input-text-prefix-trailing-space: var(--md-outlined-text-field-input-text-prefix-trailing-space, 2px);--_input-text-suffix-leading-space: var(--md-outlined-text-field-input-text-suffix-leading-space, 2px);--_focus-caret-color: var(--md-outlined-text-field-focus-caret-color, var(--md-sys-color-primary, #6750a4));--_with-leading-icon-leading-space: var(--md-outlined-text-field-with-leading-icon-leading-space, 12px);--_with-trailing-icon-trailing-space: var(--md-outlined-text-field-with-trailing-icon-trailing-space, 12px);--md-outlined-field-bottom-space: var(--_bottom-space);--md-outlined-field-container-shape-end-end: var(--_container-shape-end-end);--md-outlined-field-container-shape-end-start: var(--_container-shape-end-start);--md-outlined-field-container-shape-start-end: var(--_container-shape-start-end);--md-outlined-field-container-shape-start-start: var(--_container-shape-start-start);--md-outlined-field-content-color: var(--_input-text-color);--md-outlined-field-content-font: var(--_input-text-font);--md-outlined-field-content-line-height: var(--_input-text-line-height);--md-outlined-field-content-size: var(--_input-text-size);--md-outlined-field-content-space: var(--_icon-input-space);--md-outlined-field-content-weight: var(--_input-text-weight);--md-outlined-field-disabled-content-color: var(--_disabled-input-text-color);--md-outlined-field-disabled-content-opacity: var(--_disabled-input-text-opacity);--md-outlined-field-disabled-label-text-color: var(--_disabled-label-text-color);--md-outlined-field-disabled-label-text-opacity: var(--_disabled-label-text-opacity);--md-outlined-field-disabled-leading-content-color: var(--_disabled-leading-icon-color);--md-outlined-field-disabled-leading-content-opacity: var(--_disabled-leading-icon-opacity);--md-outlined-field-disabled-outline-color: var(--_disabled-outline-color);--md-outlined-field-disabled-outline-opacity: var(--_disabled-outline-opacity);--md-outlined-field-disabled-outline-width: var(--_disabled-outline-width);--md-outlined-field-disabled-supporting-text-color: var(--_disabled-supporting-text-color);--md-outlined-field-disabled-supporting-text-opacity: var(--_disabled-supporting-text-opacity);--md-outlined-field-disabled-trailing-content-color: var(--_disabled-trailing-icon-color);--md-outlined-field-disabled-trailing-content-opacity: var(--_disabled-trailing-icon-opacity);--md-outlined-field-error-content-color: var(--_error-input-text-color);--md-outlined-field-error-focus-content-color: var(--_error-focus-input-text-color);--md-outlined-field-error-focus-label-text-color: var(--_error-focus-label-text-color);--md-outlined-field-error-focus-leading-content-color: var(--_error-focus-leading-icon-color);--md-outlined-field-error-focus-outline-color: var(--_error-focus-outline-color);--md-outlined-field-error-focus-supporting-text-color: var(--_error-focus-supporting-text-color);--md-outlined-field-error-focus-trailing-content-color: var(--_error-focus-trailing-icon-color);--md-outlined-field-error-hover-content-color: var(--_error-hover-input-text-color);--md-outlined-field-error-hover-label-text-color: var(--_error-hover-label-text-color);--md-outlined-field-error-hover-leading-content-color: var(--_error-hover-leading-icon-color);--md-outlined-field-error-hover-outline-color: var(--_error-hover-outline-color);--md-outlined-field-error-hover-supporting-text-color: var(--_error-hover-supporting-text-color);--md-outlined-field-error-hover-trailing-content-color: var(--_error-hover-trailing-icon-color);--md-outlined-field-error-label-text-color: var(--_error-label-text-color);--md-outlined-field-error-leading-content-color: var(--_error-leading-icon-color);--md-outlined-field-error-outline-color: var(--_error-outline-color);--md-outlined-field-error-supporting-text-color: var(--_error-supporting-text-color);--md-outlined-field-error-trailing-content-color: var(--_error-trailing-icon-color);--md-outlined-field-focus-content-color: var(--_focus-input-text-color);--md-outlined-field-focus-label-text-color: var(--_focus-label-text-color);--md-outlined-field-focus-leading-content-color: var(--_focus-leading-icon-color);--md-outlined-field-focus-outline-color: var(--_focus-outline-color);--md-outlined-field-focus-outline-width: var(--_focus-outline-width);--md-outlined-field-focus-supporting-text-color: var(--_focus-supporting-text-color);--md-outlined-field-focus-trailing-content-color: var(--_focus-trailing-icon-color);--md-outlined-field-hover-content-color: var(--_hover-input-text-color);--md-outlined-field-hover-label-text-color: var(--_hover-label-text-color);--md-outlined-field-hover-leading-content-color: var(--_hover-leading-icon-color);--md-outlined-field-hover-outline-color: var(--_hover-outline-color);--md-outlined-field-hover-outline-width: var(--_hover-outline-width);--md-outlined-field-hover-supporting-text-color: var(--_hover-supporting-text-color);--md-outlined-field-hover-trailing-content-color: var(--_hover-trailing-icon-color);--md-outlined-field-label-text-color: var(--_label-text-color);--md-outlined-field-label-text-font: var(--_label-text-font);--md-outlined-field-label-text-line-height: var(--_label-text-line-height);--md-outlined-field-label-text-populated-line-height: var(--_label-text-populated-line-height);--md-outlined-field-label-text-populated-size: var(--_label-text-populated-size);--md-outlined-field-label-text-size: var(--_label-text-size);--md-outlined-field-label-text-weight: var(--_label-text-weight);--md-outlined-field-leading-content-color: var(--_leading-icon-color);--md-outlined-field-leading-space: var(--_leading-space);--md-outlined-field-outline-color: var(--_outline-color);--md-outlined-field-outline-width: var(--_outline-width);--md-outlined-field-supporting-text-color: var(--_supporting-text-color);--md-outlined-field-supporting-text-font: var(--_supporting-text-font);--md-outlined-field-supporting-text-line-height: var(--_supporting-text-line-height);--md-outlined-field-supporting-text-size: var(--_supporting-text-size);--md-outlined-field-supporting-text-weight: var(--_supporting-text-weight);--md-outlined-field-top-space: var(--_top-space);--md-outlined-field-trailing-content-color: var(--_trailing-icon-color);--md-outlined-field-trailing-space: var(--_trailing-space);--md-outlined-field-with-leading-content-leading-space: var(--_with-leading-icon-leading-space);--md-outlined-field-with-trailing-content-trailing-space: var(--_with-trailing-icon-trailing-space)}
`;

  // node_modules/lit-html/directive-helpers.js
  var { I: et2 } = si;
  var rt = (o5) => void 0 === o5.strings;
  var ht = {};
  var dt = (o5, t4 = ht) => o5._$AH = t4;

  // node_modules/lit-html/directives/live.js
  var Ft = e5(class extends i3 {
    constructor(r5) {
      if (super(r5), r5.type !== t3.PROPERTY && r5.type !== t3.ATTRIBUTE && r5.type !== t3.BOOLEAN_ATTRIBUTE)
        throw Error("The `live` directive is not allowed on child or event bindings");
      if (!rt(r5))
        throw Error("`live` bindings can only contain a single expression");
    }
    render(r5) {
      return r5;
    }
    update(r5, [e6]) {
      if (e6 === R || e6 === D)
        return e6;
      const i4 = r5.element, n5 = r5.name;
      if (r5.type === t3.PROPERTY) {
        if (e6 === i4[n5])
          return R;
      } else if (r5.type === t3.BOOLEAN_ATTRIBUTE) {
        if (!!e6 === i4.hasAttribute(n5))
          return R;
      } else if (r5.type === t3.ATTRIBUTE && i4.getAttribute(n5) === e6 + "")
        return R;
      return dt(r5), e6;
    }
  });

  // node_modules/lit-html/directives/style-map.js
  var ee = "important";
  var ie = " !" + ee;
  var se = e5(class extends i3 {
    constructor(e6) {
      if (super(e6), e6.type !== t3.ATTRIBUTE || "style" !== e6.name || e6.strings?.length > 2)
        throw Error("The `styleMap` directive must be used in the `style` attribute and must be the only part in the attribute.");
    }
    render(t4) {
      return Object.keys(t4).reduce((e6, r5) => {
        const s2 = t4[r5];
        return null == s2 ? e6 : e6 + `${r5 = r5.includes("-") ? r5 : r5.replace(/(?:^(webkit|moz|ms|o)|)(?=[A-Z])/g, "-$&").toLowerCase()}:${s2};`;
      }, "");
    }
    update(t4, [e6]) {
      const { style: r5 } = t4.element;
      if (void 0 === this.ft)
        return this.ft = new Set(Object.keys(e6)), this.render(e6);
      for (const t5 of this.ft)
        null == e6[t5] && (this.ft.delete(t5), t5.includes("-") ? r5.removeProperty(t5) : r5[t5] = null);
      for (const t5 in e6) {
        const s2 = e6[t5];
        if (null != s2) {
          this.ft.add(t5);
          const e7 = "string" == typeof s2 && s2.endsWith(ie);
          t5.includes("-") || e7 ? r5.setProperty(t5, e7 ? s2.slice(0, -11) : s2, e7 ? ee : "") : r5[t5] = s2;
        }
      }
      return R;
    }
  });

  // node_modules/@material/web/internal/controller/string-converter.js
  var stringConverter = {
    fromAttribute(value) {
      return value ?? "";
    },
    toAttribute(value) {
      return value || null;
    }
  };

  // node_modules/@material/web/labs/behaviors/on-report-validity.js
  var onReportValidity = Symbol("onReportValidity");
  var privateCleanupFormListeners = Symbol("privateCleanupFormListeners");
  var privateDoNotReportInvalid = Symbol("privateDoNotReportInvalid");
  var privateIsSelfReportingValidity = Symbol("privateIsSelfReportingValidity");
  var privateCallOnReportValidity = Symbol("privateCallOnReportValidity");
  function mixinOnReportValidity(base) {
    var _a2, _b, _c;
    class OnReportValidityElement extends base {
      // Mixins must have a constructor with `...args: any[]`
      // tslint:disable-next-line:no-any
      constructor(...args) {
        super(...args);
        this[_a2] = new AbortController();
        this[_b] = false;
        this[_c] = false;
        if (co) {
          return;
        }
        this.addEventListener("invalid", (invalidEvent) => {
          if (this[privateDoNotReportInvalid] || !invalidEvent.isTrusted) {
            return;
          }
          this.addEventListener("invalid", () => {
            this[privateCallOnReportValidity](invalidEvent);
          }, { once: true });
        }, {
          // Listen during the capture phase, which will happen before the
          // bubbling phase. That way, we can add a final event listener that
          // will run after other event listeners, and we can check if it was
          // default prevented. This works because invalid does not bubble.
          capture: true
        });
      }
      checkValidity() {
        this[privateDoNotReportInvalid] = true;
        const valid = super.checkValidity();
        this[privateDoNotReportInvalid] = false;
        return valid;
      }
      reportValidity() {
        this[privateIsSelfReportingValidity] = true;
        const valid = super.reportValidity();
        if (valid) {
          this[privateCallOnReportValidity](null);
        }
        this[privateIsSelfReportingValidity] = false;
        return valid;
      }
      [(_a2 = privateCleanupFormListeners, _b = privateDoNotReportInvalid, _c = privateIsSelfReportingValidity, privateCallOnReportValidity)](invalidEvent) {
        const wasCanceled = invalidEvent?.defaultPrevented;
        if (wasCanceled) {
          return;
        }
        this[onReportValidity](invalidEvent);
        const implementationCanceledFocus = !wasCanceled && invalidEvent?.defaultPrevented;
        if (!implementationCanceledFocus) {
          return;
        }
        if (this[privateIsSelfReportingValidity] || isFirstInvalidControlInForm(this[internals].form, this)) {
          this.focus();
        }
      }
      [onReportValidity](invalidEvent) {
        throw new Error("Implement [onReportValidity]");
      }
      formAssociatedCallback(form) {
        if (super.formAssociatedCallback) {
          super.formAssociatedCallback(form);
        }
        this[privateCleanupFormListeners].abort();
        if (!form) {
          return;
        }
        this[privateCleanupFormListeners] = new AbortController();
        addFormReportValidListener(this, form, () => {
          this[privateCallOnReportValidity](null);
        }, this[privateCleanupFormListeners].signal);
      }
    }
    return OnReportValidityElement;
  }
  function addFormReportValidListener(control, form, onControlValid, cleanup) {
    const validateHooks = getFormValidateHooks(form);
    let controlFiredInvalid = false;
    let cleanupInvalidListener;
    let isNextSubmitFromHook = false;
    validateHooks.addEventListener("before", () => {
      isNextSubmitFromHook = true;
      cleanupInvalidListener = new AbortController();
      controlFiredInvalid = false;
      control.addEventListener("invalid", () => {
        controlFiredInvalid = true;
      }, {
        signal: cleanupInvalidListener.signal
      });
    }, { signal: cleanup });
    validateHooks.addEventListener("after", () => {
      isNextSubmitFromHook = false;
      cleanupInvalidListener?.abort();
      if (controlFiredInvalid) {
        return;
      }
      onControlValid();
    }, { signal: cleanup });
    form.addEventListener("submit", () => {
      if (isNextSubmitFromHook) {
        return;
      }
      onControlValid();
    }, {
      signal: cleanup
    });
  }
  var FORM_VALIDATE_HOOKS = /* @__PURE__ */ new WeakMap();
  function getFormValidateHooks(form) {
    if (!FORM_VALIDATE_HOOKS.has(form)) {
      const hooks = new EventTarget();
      FORM_VALIDATE_HOOKS.set(form, hooks);
      for (const methodName of ["reportValidity", "requestSubmit"]) {
        const superMethod = form[methodName];
        form[methodName] = function() {
          hooks.dispatchEvent(new Event("before"));
          const result = Reflect.apply(superMethod, this, arguments);
          hooks.dispatchEvent(new Event("after"));
          return result;
        };
      }
    }
    return FORM_VALIDATE_HOOKS.get(form);
  }
  function isFirstInvalidControlInForm(form, control) {
    if (!form) {
      return true;
    }
    let firstInvalidControl;
    for (const element of form.elements) {
      if (element.matches(":invalid")) {
        firstInvalidControl = element;
        break;
      }
    }
    return firstInvalidControl === control;
  }

  // node_modules/@material/web/labs/behaviors/validators/text-field-validator.js
  var TextFieldValidator = class extends Validator {
    computeValidity({ state, renderedControl }) {
      let inputOrTextArea = renderedControl;
      if (isInputState(state) && !inputOrTextArea) {
        inputOrTextArea = this.inputControl || document.createElement("input");
        this.inputControl = inputOrTextArea;
      } else if (!inputOrTextArea) {
        inputOrTextArea = this.textAreaControl || document.createElement("textarea");
        this.textAreaControl = inputOrTextArea;
      }
      const input = isInputState(state) ? inputOrTextArea : null;
      if (input) {
        input.type = state.type;
      }
      if (inputOrTextArea.value !== state.value) {
        inputOrTextArea.value = state.value;
      }
      inputOrTextArea.required = state.required;
      if (input) {
        const inputState = state;
        if (inputState.pattern) {
          input.pattern = inputState.pattern;
        } else {
          input.removeAttribute("pattern");
        }
        if (inputState.min) {
          input.min = inputState.min;
        } else {
          input.removeAttribute("min");
        }
        if (inputState.max) {
          input.max = inputState.max;
        } else {
          input.removeAttribute("max");
        }
        if (inputState.step) {
          input.step = inputState.step;
        } else {
          input.removeAttribute("step");
        }
      }
      if ((state.minLength ?? -1) > -1) {
        inputOrTextArea.setAttribute("minlength", String(state.minLength));
      } else {
        inputOrTextArea.removeAttribute("minlength");
      }
      if ((state.maxLength ?? -1) > -1) {
        inputOrTextArea.setAttribute("maxlength", String(state.maxLength));
      } else {
        inputOrTextArea.removeAttribute("maxlength");
      }
      return {
        validity: inputOrTextArea.validity,
        validationMessage: inputOrTextArea.validationMessage
      };
    }
    equals({ state: prev }, { state: next }) {
      const inputOrTextAreaEqual = prev.type === next.type && prev.value === next.value && prev.required === next.required && prev.minLength === next.minLength && prev.maxLength === next.maxLength;
      if (!isInputState(prev) || !isInputState(next)) {
        return inputOrTextAreaEqual;
      }
      return inputOrTextAreaEqual && prev.pattern === next.pattern && prev.min === next.min && prev.max === next.max && prev.step === next.step;
    }
    copy({ state }) {
      return {
        state: isInputState(state) ? this.copyInput(state) : this.copyTextArea(state),
        renderedControl: null
      };
    }
    copyInput(state) {
      const { type, pattern, min, max, step } = state;
      return {
        ...this.copySharedState(state),
        type,
        pattern,
        min,
        max,
        step
      };
    }
    copyTextArea(state) {
      return {
        ...this.copySharedState(state),
        type: state.type
      };
    }
    copySharedState({ value, required, minLength, maxLength }) {
      return { value, required, minLength, maxLength };
    }
  };
  function isInputState(state) {
    return state.type !== "textarea";
  }

  // node_modules/@material/web/textfield/internal/text-field.js
  var textFieldBaseClass = mixinDelegatesAria(mixinOnReportValidity(mixinConstraintValidation(mixinFormAssociated(mixinElementInternals(h3)))));
  var TextField = class extends textFieldBaseClass {
    constructor() {
      super(...arguments);
      this.error = false;
      this.errorText = "";
      this.label = "";
      this.noAsterisk = false;
      this.required = false;
      this.value = "";
      this.prefixText = "";
      this.suffixText = "";
      this.hasLeadingIcon = false;
      this.hasTrailingIcon = false;
      this.supportingText = "";
      this.textDirection = "";
      this.rows = 2;
      this.cols = 20;
      this.inputMode = "";
      this.max = "";
      this.maxLength = -1;
      this.min = "";
      this.minLength = -1;
      this.noSpinner = false;
      this.pattern = "";
      this.placeholder = "";
      this.readOnly = false;
      this.multiple = false;
      this.step = "";
      this.type = "text";
      this.autocomplete = "";
      this.dirty = false;
      this.focused = false;
      this.nativeError = false;
      this.nativeErrorText = "";
    }
    /**
     * Gets or sets the direction in which selection occurred.
     */
    get selectionDirection() {
      return this.getInputOrTextarea().selectionDirection;
    }
    set selectionDirection(value) {
      this.getInputOrTextarea().selectionDirection = value;
    }
    /**
     * Gets or sets the end position or offset of a text selection.
     */
    get selectionEnd() {
      return this.getInputOrTextarea().selectionEnd;
    }
    set selectionEnd(value) {
      this.getInputOrTextarea().selectionEnd = value;
    }
    /**
     * Gets or sets the starting position or offset of a text selection.
     */
    get selectionStart() {
      return this.getInputOrTextarea().selectionStart;
    }
    set selectionStart(value) {
      this.getInputOrTextarea().selectionStart = value;
    }
    /**
     * The text field's value as a number.
     */
    get valueAsNumber() {
      const input = this.getInput();
      if (!input) {
        return NaN;
      }
      return input.valueAsNumber;
    }
    set valueAsNumber(value) {
      const input = this.getInput();
      if (!input) {
        return;
      }
      input.valueAsNumber = value;
      this.value = input.value;
    }
    /**
     * The text field's value as a Date.
     */
    get valueAsDate() {
      const input = this.getInput();
      if (!input) {
        return null;
      }
      return input.valueAsDate;
    }
    set valueAsDate(value) {
      const input = this.getInput();
      if (!input) {
        return;
      }
      input.valueAsDate = value;
      this.value = input.value;
    }
    get hasError() {
      return this.error || this.nativeError;
    }
    /**
     * Selects all the text in the text field.
     *
     * https://developer.mozilla.org/en-US/docs/Web/API/HTMLInputElement/select
     */
    select() {
      this.getInputOrTextarea().select();
    }
    setRangeText(...args) {
      this.getInputOrTextarea().setRangeText(...args);
      this.value = this.getInputOrTextarea().value;
    }
    /**
     * Sets the start and end positions of a selection in the text field.
     *
     * https://developer.mozilla.org/en-US/docs/Web/API/HTMLInputElement/setSelectionRange
     *
     * @param start The offset into the text field for the start of the selection.
     * @param end The offset into the text field for the end of the selection.
     * @param direction The direction in which the selection is performed.
     */
    setSelectionRange(start, end, direction) {
      this.getInputOrTextarea().setSelectionRange(start, end, direction);
    }
    /**
     * Decrements the value of a numeric type text field by `step` or `n` `step`
     * number of times.
     *
     * https://developer.mozilla.org/en-US/docs/Web/API/HTMLInputElement/stepDown
     *
     * @param stepDecrement The number of steps to decrement, defaults to 1.
     */
    stepDown(stepDecrement) {
      const input = this.getInput();
      if (!input) {
        return;
      }
      input.stepDown(stepDecrement);
      this.value = input.value;
    }
    /**
     * Increments the value of a numeric type text field by `step` or `n` `step`
     * number of times.
     *
     * https://developer.mozilla.org/en-US/docs/Web/API/HTMLInputElement/stepUp
     *
     * @param stepIncrement The number of steps to increment, defaults to 1.
     */
    stepUp(stepIncrement) {
      const input = this.getInput();
      if (!input) {
        return;
      }
      input.stepUp(stepIncrement);
      this.value = input.value;
    }
    /**
     * Reset the text field to its default value.
     */
    reset() {
      this.dirty = false;
      this.value = this.getAttribute("value") ?? "";
      this.nativeError = false;
      this.nativeErrorText = "";
    }
    attributeChangedCallback(attribute, newValue, oldValue) {
      if (attribute === "value" && this.dirty) {
        return;
      }
      super.attributeChangedCallback(attribute, newValue, oldValue);
    }
    render() {
      const classes = {
        "disabled": this.disabled,
        "error": !this.disabled && this.hasError,
        "textarea": this.type === "textarea",
        "no-spinner": this.noSpinner
      };
      return ke`
      <span class="text-field ${Rt(classes)}">
        ${this.renderField()}
      </span>
    `;
    }
    updated(changedProperties) {
      const value = this.getInputOrTextarea().value;
      if (this.value !== value) {
        this.value = value;
      }
    }
    renderField() {
      return ke2`<${this.fieldTag}
      class="field"
      count=${this.value.length}
      ?disabled=${this.disabled}
      ?error=${this.hasError}
      error-text=${this.getErrorText()}
      ?focused=${this.focused}
      ?has-end=${this.hasTrailingIcon}
      ?has-start=${this.hasLeadingIcon}
      label=${this.label}
      ?no-asterisk=${this.noAsterisk}
      max=${this.maxLength}
      ?populated=${!!this.value}
      ?required=${this.required}
      ?resizable=${this.type === "textarea"}
      supporting-text=${this.supportingText}
    >
      ${this.renderLeadingIcon()}
      ${this.renderInputOrTextarea()}
      ${this.renderTrailingIcon()}
      <div id="description" slot="aria-describedby"></div>
    </${this.fieldTag}>`;
    }
    renderLeadingIcon() {
      return ke`
      <span class="icon leading" slot="start">
        <slot name="leading-icon" @slotchange=${this.handleIconChange}></slot>
      </span>
    `;
    }
    renderTrailingIcon() {
      return ke`
      <span class="icon trailing" slot="end">
        <slot name="trailing-icon" @slotchange=${this.handleIconChange}></slot>
      </span>
    `;
    }
    renderInputOrTextarea() {
      const style = { "direction": this.textDirection };
      const ariaLabel = this.ariaLabel || this.label || D;
      const autocomplete = this.autocomplete;
      const hasMaxLength = (this.maxLength ?? -1) > -1;
      const hasMinLength = (this.minLength ?? -1) > -1;
      if (this.type === "textarea") {
        return ke`
        <textarea
          class="input"
          style=${se(style)}
          aria-describedby="description"
          aria-invalid=${this.hasError}
          aria-label=${ariaLabel}
          autocomplete=${autocomplete || D}
          name=${this.name || D}
          ?disabled=${this.disabled}
          maxlength=${hasMaxLength ? this.maxLength : D}
          minlength=${hasMinLength ? this.minLength : D}
          placeholder=${this.placeholder || D}
          ?readonly=${this.readOnly}
          ?required=${this.required}
          rows=${this.rows}
          cols=${this.cols}
          .value=${Ft(this.value)}
          @change=${this.redispatchEvent}
          @focus=${this.handleFocusChange}
          @blur=${this.handleFocusChange}
          @input=${this.handleInput}
          @select=${this.redispatchEvent}></textarea>
      `;
      }
      const prefix = this.renderPrefix();
      const suffix = this.renderSuffix();
      const inputMode = this.inputMode;
      return ke`
      <div class="input-wrapper">
        ${prefix}
        <input
          class="input"
          style=${se(style)}
          aria-describedby="description"
          aria-invalid=${this.hasError}
          aria-label=${ariaLabel}
          autocomplete=${autocomplete || D}
          name=${this.name || D}
          ?disabled=${this.disabled}
          inputmode=${inputMode || D}
          max=${this.max || D}
          maxlength=${hasMaxLength ? this.maxLength : D}
          min=${this.min || D}
          minlength=${hasMinLength ? this.minLength : D}
          pattern=${this.pattern || D}
          placeholder=${this.placeholder || D}
          ?readonly=${this.readOnly}
          ?required=${this.required}
          ?multiple=${this.multiple}
          step=${this.step || D}
          type=${this.type}
          .value=${Ft(this.value)}
          @change=${this.redispatchEvent}
          @focus=${this.handleFocusChange}
          @blur=${this.handleFocusChange}
          @input=${this.handleInput}
          @select=${this.redispatchEvent} />
        ${suffix}
      </div>
    `;
    }
    renderPrefix() {
      return this.renderAffix(
        this.prefixText,
        /* isSuffix */
        false
      );
    }
    renderSuffix() {
      return this.renderAffix(
        this.suffixText,
        /* isSuffix */
        true
      );
    }
    renderAffix(text, isSuffix) {
      if (!text) {
        return D;
      }
      const classes = {
        "suffix": isSuffix,
        "prefix": !isSuffix
      };
      return ke`<span class="${Rt(classes)}">${text}</span>`;
    }
    getErrorText() {
      return this.error ? this.errorText : this.nativeErrorText;
    }
    handleFocusChange() {
      this.focused = this.inputOrTextarea?.matches(":focus") ?? false;
    }
    handleInput(event) {
      this.dirty = true;
      this.value = event.target.value;
    }
    redispatchEvent(event) {
      redispatchEvent(this, event);
    }
    getInputOrTextarea() {
      if (!this.inputOrTextarea) {
        this.connectedCallback();
        this.scheduleUpdate();
      }
      if (this.isUpdatePending) {
        this.scheduleUpdate();
      }
      return this.inputOrTextarea;
    }
    getInput() {
      if (this.type === "textarea") {
        return null;
      }
      return this.getInputOrTextarea();
    }
    handleIconChange() {
      this.hasLeadingIcon = this.leadingIcons.length > 0;
      this.hasTrailingIcon = this.trailingIcons.length > 0;
    }
    [getFormValue]() {
      return this.value;
    }
    formResetCallback() {
      this.reset();
    }
    formStateRestoreCallback(state) {
      this.value = state;
    }
    focus() {
      this.getInputOrTextarea().focus();
    }
    [createValidator]() {
      return new TextFieldValidator(() => ({
        state: this,
        renderedControl: this.inputOrTextarea
      }));
    }
    [getValidityAnchor]() {
      return this.inputOrTextarea;
    }
    [onReportValidity](invalidEvent) {
      invalidEvent?.preventDefault();
      const prevMessage = this.getErrorText();
      this.nativeError = !!invalidEvent;
      this.nativeErrorText = this.validationMessage;
      if (prevMessage === this.getErrorText()) {
        this.field?.reannounceError();
      }
    }
  };
  TextField.shadowRootOptions = {
    ...h3.shadowRootOptions,
    delegatesFocus: true
  };
  __decorate([
    n4({ type: Boolean, reflect: true })
  ], TextField.prototype, "error", void 0);
  __decorate([
    n4({ attribute: "error-text" })
  ], TextField.prototype, "errorText", void 0);
  __decorate([
    n4()
  ], TextField.prototype, "label", void 0);
  __decorate([
    n4({ type: Boolean, attribute: "no-asterisk" })
  ], TextField.prototype, "noAsterisk", void 0);
  __decorate([
    n4({ type: Boolean, reflect: true })
  ], TextField.prototype, "required", void 0);
  __decorate([
    n4()
  ], TextField.prototype, "value", void 0);
  __decorate([
    n4({ attribute: "prefix-text" })
  ], TextField.prototype, "prefixText", void 0);
  __decorate([
    n4({ attribute: "suffix-text" })
  ], TextField.prototype, "suffixText", void 0);
  __decorate([
    n4({ type: Boolean, attribute: "has-leading-icon" })
  ], TextField.prototype, "hasLeadingIcon", void 0);
  __decorate([
    n4({ type: Boolean, attribute: "has-trailing-icon" })
  ], TextField.prototype, "hasTrailingIcon", void 0);
  __decorate([
    n4({ attribute: "supporting-text" })
  ], TextField.prototype, "supportingText", void 0);
  __decorate([
    n4({ attribute: "text-direction" })
  ], TextField.prototype, "textDirection", void 0);
  __decorate([
    n4({ type: Number })
  ], TextField.prototype, "rows", void 0);
  __decorate([
    n4({ type: Number })
  ], TextField.prototype, "cols", void 0);
  __decorate([
    n4({ reflect: true })
  ], TextField.prototype, "inputMode", void 0);
  __decorate([
    n4()
  ], TextField.prototype, "max", void 0);
  __decorate([
    n4({ type: Number })
  ], TextField.prototype, "maxLength", void 0);
  __decorate([
    n4()
  ], TextField.prototype, "min", void 0);
  __decorate([
    n4({ type: Number })
  ], TextField.prototype, "minLength", void 0);
  __decorate([
    n4({ type: Boolean, attribute: "no-spinner" })
  ], TextField.prototype, "noSpinner", void 0);
  __decorate([
    n4()
  ], TextField.prototype, "pattern", void 0);
  __decorate([
    n4({ reflect: true, converter: stringConverter })
  ], TextField.prototype, "placeholder", void 0);
  __decorate([
    n4({ type: Boolean, reflect: true })
  ], TextField.prototype, "readOnly", void 0);
  __decorate([
    n4({ type: Boolean, reflect: true })
  ], TextField.prototype, "multiple", void 0);
  __decorate([
    n4()
  ], TextField.prototype, "step", void 0);
  __decorate([
    n4({ reflect: true })
  ], TextField.prototype, "type", void 0);
  __decorate([
    n4({ reflect: true })
  ], TextField.prototype, "autocomplete", void 0);
  __decorate([
    r4()
  ], TextField.prototype, "dirty", void 0);
  __decorate([
    r4()
  ], TextField.prototype, "focused", void 0);
  __decorate([
    r4()
  ], TextField.prototype, "nativeError", void 0);
  __decorate([
    r4()
  ], TextField.prototype, "nativeErrorText", void 0);
  __decorate([
    e4(".input")
  ], TextField.prototype, "inputOrTextarea", void 0);
  __decorate([
    e4(".field")
  ], TextField.prototype, "field", void 0);
  __decorate([
    o4({ slot: "leading-icon" })
  ], TextField.prototype, "leadingIcons", void 0);
  __decorate([
    o4({ slot: "trailing-icon" })
  ], TextField.prototype, "trailingIcons", void 0);

  // node_modules/@material/web/textfield/internal/outlined-text-field.js
  var OutlinedTextField = class extends TextField {
    constructor() {
      super(...arguments);
      this.fieldTag = er`md-outlined-field`;
    }
  };

  // node_modules/@material/web/textfield/internal/shared-styles.js
  var styles18 = i`:host{display:inline-flex;outline:none;resize:both;text-align:start;-webkit-tap-highlight-color:rgba(0,0,0,0)}.text-field,.field{width:100%}.text-field{display:inline-flex}.field{cursor:text}.disabled .field{cursor:default}.text-field,.textarea .field{resize:inherit}.icon{color:currentColor;display:flex;align-items:center;justify-content:center;fill:currentColor;position:relative}.icon ::slotted(*){display:flex;position:absolute}[has-start] .icon.leading{font-size:var(--_leading-icon-size);height:var(--_leading-icon-size);width:var(--_leading-icon-size)}[has-end] .icon.trailing{font-size:var(--_trailing-icon-size);height:var(--_trailing-icon-size);width:var(--_trailing-icon-size)}.input-wrapper{display:flex}.input-wrapper>*{all:inherit;padding:0}.input{caret-color:var(--_caret-color);overflow-x:hidden;text-align:inherit}.input::placeholder{color:currentColor;opacity:1}.input::-webkit-calendar-picker-indicator{display:none}.input::-webkit-search-decoration,.input::-webkit-search-cancel-button{display:none}@media(forced-colors: active){.input{background:none}}.no-spinner .input::-webkit-inner-spin-button,.no-spinner .input::-webkit-outer-spin-button{display:none}.no-spinner .input[type=number]{-moz-appearance:textfield}:focus-within .input{caret-color:var(--_focus-caret-color)}.error:focus-within .input{caret-color:var(--_error-focus-caret-color)}.text-field:not(.disabled) .prefix{color:var(--_input-text-prefix-color)}.text-field:not(.disabled) .suffix{color:var(--_input-text-suffix-color)}.text-field:not(.disabled) .input::placeholder{color:var(--_input-text-placeholder-color)}.prefix,.suffix{text-wrap:nowrap;width:min-content}.prefix{padding-inline-end:var(--_input-text-prefix-trailing-space)}.suffix{padding-inline-start:var(--_input-text-suffix-leading-space)}
`;

  // node_modules/@material/web/textfield/outlined-text-field.js
  var MdOutlinedTextField = class MdOutlinedTextField2 extends OutlinedTextField {
    constructor() {
      super(...arguments);
      this.fieldTag = er`md-outlined-field`;
    }
  };
  MdOutlinedTextField.styles = [styles18, styles17];
  MdOutlinedTextField = __decorate([
    t2("md-outlined-text-field")
  ], MdOutlinedTextField);

  // v2/custom-elements/android-breakage-dialog.js
  var AndroidBreakageDialog = class extends h3 {
    constructor() {
      super(...arguments);
      __publicField(this, "title", "please set the title prop");
      __publicField(this, "okText", "OK");
      __publicField(this, "cancelText", "Cancel");
      /** @type {[string, string][]} */
      __publicField(this, "items", []);
      __publicField(this, "opened", false);
    }
    /**
     * Every time we show the modal, reset the form to
     * ensure it's in sync with the one in the page
     *
     * @param {string} initial - the category value
     */
    show(initial) {
      if (!this.shadowRoot)
        throw new Error("unreachable");
      const elem = this.shadowRoot?.querySelector("md-dialog");
      if (!elem)
        throw new Error("unreachable");
      const form = elem?.querySelector("form");
      if (!form)
        throw new Error("unreachable, missing form");
      const radioElements = this.shadowRoot?.querySelectorAll("md-radio");
      form.reset();
      for (const element of Array.from(radioElements)) {
        if (element.value === initial) {
          element.checked = true;
        }
      }
      elem.show();
    }
    get _currentFormValue() {
      const elem = this.shadowRoot?.querySelector("md-dialog");
      if (!elem)
        throw new Error("unreachable");
      const form = elem?.querySelector("form");
      if (!form)
        throw new Error("unreachable, missing form");
      const formData = new FormData(form);
      const value = formData.get("category");
      return value;
    }
    _onClose(e6) {
      const dialog = e6.target;
      if (dialog.returnValue === "ok") {
        const value = this._currentFormValue;
        const options = { detail: { value }, composed: true, bubbles: true };
        const event = new CustomEvent("did-select", options);
        this.dispatchEvent(event);
      }
    }
    render() {
      return ke`
            <md-dialog @close=${this._onClose}>
                <div slot="headline">${this.title}</div>
                <form slot="content" id="form" method="dialog">
                    ${oo(this.items, ([value, title]) => {
        return ke`
                            <label>
                                <md-radio name="category" value=${value} aria-label=${title} touch-target="wrapper"></md-radio>
                                <span aria-hidden="true">${title}</span>
                            </label>
                        `;
      })}
                </form>
                <div slot="actions">
                    <!-- added autofocus as mentioned here: https://developer.mozilla.org/en-US/docs/Web/HTML/Element/dialog#usage_notes -->
                    <md-text-button form="form" value="cancel" autofocus>${this.cancelText}</md-text-button>
                    <md-filled-button form="form" value="ok">${this.okText}</md-filled-button>
                </div>
            </md-dialog>
        `;
    }
  };
  __publicField(AndroidBreakageDialog, "properties", {
    items: {
      type: Array
    },
    title: { type: String },
    okText: { type: String },
    cancelText: { type: String },
    opened: {
      type: Boolean
    }
  });
  __publicField(AndroidBreakageDialog, "styles", [
    i`
            :root {
                text-align: left;
            }
            label {
                display: flex;
                align-items: center;
            }
            label md-radio {
                flex-shrink: 0;
            }
            md-dialog {
                text-align: left;

                --md-ref-typeface-plain: system, system-ui, Roboto;
                --md-ref-typeface-brand: system, system-ui, Roboto;

                --md-sys-typescale-headline-small-weight: 500;
                --md-dialog-headline-size: 20px;
                --md-dialog-container-shape: 12px;
                --md-dialog-container-color: white;

                --md-sys-color-primary: #3969ef;
                --md-sys-color-on-primary: white;
                --md-radio-icon-color: #3969ef;
                --md-sys-typescale-body-medium-size: 1rem;
            }

            @media screen and (prefers-color-scheme: dark) {
                md-dialog {
                    --md-sys-color-primary: #7295f6;
                    --md-radio-icon-color: #7295f6;
                    --md-dialog-container-color: #282828;
                    --md-sys-color-on-primary: black;
                    --md-sys-color-on-surface: rgba(255, 255, 255, 0.9);
                    --md-sys-color-on-surface-variant: rgba(255, 255, 255, 0.9);
                    --md-sys-color-outline-variant: rgba(255, 255, 255, 0.18);
                }
            }

            md-filled-button {
                --md-filled-button-container-shape: 8px;
            }

            md-text-button {
                --md-text-button-container-shape: 8px;
            }
        `
  ]);
  customElements.define("ddg-android-breakage-dialog", AndroidBreakageDialog);
})();
/*! Bundled license information:

@lit/reactive-element/css-tag.js:
  (**
   * @license
   * Copyright 2019 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@lit/reactive-element/reactive-element.js:
  (**
   * @license
   * Copyright 2017 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

lit-html/lit-html.js:
  (**
   * @license
   * Copyright 2017 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

lit-element/lit-element.js:
  (**
   * @license
   * Copyright 2017 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

lit-html/is-server.js:
  (**
   * @license
   * Copyright 2022 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

lit-html/directives/map.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@lit/reactive-element/decorators/custom-element.js:
  (**
   * @license
   * Copyright 2017 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@lit/reactive-element/decorators/property.js:
  (**
   * @license
   * Copyright 2017 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@lit/reactive-element/decorators/state.js:
  (**
   * @license
   * Copyright 2017 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@lit/reactive-element/decorators/event-options.js:
  (**
   * @license
   * Copyright 2017 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@lit/reactive-element/decorators/base.js:
  (**
   * @license
   * Copyright 2017 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@lit/reactive-element/decorators/query.js:
  (**
   * @license
   * Copyright 2017 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@lit/reactive-element/decorators/query-all.js:
  (**
   * @license
   * Copyright 2017 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@lit/reactive-element/decorators/query-async.js:
  (**
   * @license
   * Copyright 2017 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@lit/reactive-element/decorators/query-assigned-elements.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@lit/reactive-element/decorators/query-assigned-nodes.js:
  (**
   * @license
   * Copyright 2017 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@material/web/elevation/internal/elevation.js:
  (**
   * @license
   * Copyright 2022 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/elevation/internal/elevation-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/elevation/elevation.js:
  (**
   * @license
   * Copyright 2022 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/internal/controller/attachable-controller.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/focus/internal/focus-ring.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/focus/internal/focus-ring-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/focus/md-focus-ring.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

lit-html/directive.js:
  (**
   * @license
   * Copyright 2017 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

lit-html/directives/class-map.js:
  (**
   * @license
   * Copyright 2018 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@material/web/internal/motion/animation.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/ripple/internal/ripple.js:
  (**
   * @license
   * Copyright 2022 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/ripple/internal/ripple-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/ripple/ripple.js:
  (**
   * @license
   * Copyright 2022 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/internal/aria/aria.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/internal/aria/delegate.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/labs/behaviors/element-internals.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/internal/controller/form-submitter.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/internal/events/form-label-activation.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/button/internal/button.js:
  (**
   * @license
   * Copyright 2019 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/button/internal/filled-button.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/button/internal/filled-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/button/internal/shared-elevation-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/button/internal/shared-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/button/filled-button.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/button/internal/filled-tonal-button.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/button/internal/filled-tonal-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/button/filled-tonal-button.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/button/internal/text-button.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/button/internal/text-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/button/text-button.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/divider/internal/divider.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/divider/internal/divider-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/divider/divider.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/internal/events/redispatch-event.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/dialog/internal/animations.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/dialog/internal/dialog.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/dialog/internal/dialog-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/dialog/dialog.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/icon/internal/icon.js:
  (**
   * @license
   * Copyright 2022 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/icon/internal/icon-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/icon/icon.js:
  (**
   * @license
   * Copyright 2022 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

lit-html/static.js:
  (**
   * @license
   * Copyright 2020 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@material/web/internal/controller/is-rtl.js:
  (**
   * @license
   * Copyright 2022 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/iconbutton/internal/icon-button.js:
  (**
   * @license
   * Copyright 2018 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/iconbutton/internal/shared-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/iconbutton/internal/standard-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/iconbutton/icon-button.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/labs/behaviors/constraint-validation.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/labs/behaviors/focusable.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/labs/behaviors/form-associated.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/labs/behaviors/validators/validator.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/labs/behaviors/validators/radio-validator.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/radio/internal/single-selection-controller.js:
  (**
   * @license
   * Copyright 2022 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/radio/internal/radio.js:
  (**
   * @license
   * Copyright 2018 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/radio/internal/radio-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/radio/radio.js:
  (**
   * @license
   * Copyright 2022 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/field/internal/field.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/field/internal/outlined-field.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/field/internal/outlined-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/field/internal/shared-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/field/outlined-field.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/textfield/internal/outlined-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

lit-html/directive-helpers.js:
  (**
   * @license
   * Copyright 2020 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

lit-html/directives/live.js:
  (**
   * @license
   * Copyright 2020 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

lit-html/directives/style-map.js:
  (**
   * @license
   * Copyright 2018 Google LLC
   * SPDX-License-Identifier: BSD-3-Clause
   *)

@material/web/internal/controller/string-converter.js:
  (**
   * @license
   * Copyright 2022 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/labs/behaviors/on-report-validity.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/labs/behaviors/validators/text-field-validator.js:
  (**
   * @license
   * Copyright 2023 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/textfield/internal/text-field.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/textfield/internal/outlined-text-field.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/textfield/internal/shared-styles.js:
  (**
   * @license
   * Copyright 2024 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)

@material/web/textfield/outlined-text-field.js:
  (**
   * @license
   * Copyright 2021 Google LLC
   * SPDX-License-Identifier: Apache-2.0
   *)
*/
