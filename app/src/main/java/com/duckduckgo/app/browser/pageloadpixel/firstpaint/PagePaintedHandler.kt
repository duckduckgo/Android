/*
 * Copyright (c) 2024 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser.pageloadpixel.firstpaint

import android.util.Log
import android.webkit.WebView
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.browser.pageloadpixel.PageLoadedSites.Companion.sites
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

interface PagePaintedHandler {
    operator fun invoke(
        webView: WebView,
        url: String,
    )
}

@ContributesBinding(AppScope::class)
class RealPagePaintedHandler @Inject constructor(
    private val deviceInfo: DeviceInfo,
    private val webViewVersionProvider: WebViewVersionProvider,
    private val dao: PagePaintedPixelDao,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : PagePaintedHandler {

    private val job = ConflatedJob()

    override operator fun invoke(
        webView: WebView,
        url: String,
    ) {
        job += appCoroutineScope.launch(dispatcherProvider.io()) {
            if (sites.any { UriString.sameOrSubdomain(url, it) }) {
                kotlin.runCatching {
                    val firstPaint = webView.extractPagePaintDurations().toDoubleOrNull()?.roundToLong() ?: return@launch
                    dao.add(
                        PagePaintedPixelEntity(
                            appVersion = deviceInfo.appVersion,
                            webViewVersion = webViewVersionProvider.getMajorVersion(),
                            elapsedTimeFirstPaint = firstPaint,
                        ),
                    )

                    Timber.v("First-paint duration extracted: %dms for %s", firstPaint, url)
                }
            }
        }
    }

    private suspend fun WebView.extractPagePaintDurations(): String {
        return withContext(dispatcherProvider.main()) {
            suspendCoroutine { continuation ->
                evaluateJavascript(PAGE_PAINT_JS) { value ->
                    Log.i("PagePainterHandler", "DDG ADDDOCUMENTSTARTDISABLED PERF METRICS INJECTED")
                    continuation.resume(value)
                }
            }
        }
    }

    companion object {
        private val PAGE_PAINT_JS = """(()=>{var D=Object.create;var x=Object.defineProperty;var q=Object.getOwnPropertyDescriptor;var F=Object.getOwnPropertyNames;var G=Object.getPrototypeOf,A=Object.prototype.hasOwnProperty;var K=(i,s)=>()=>(s||i((s={exports:{}}).exports,s),s.exports);var J=(i,s,u,a)=>{if(s&&typeof s=="object"||typeof s=="function")for(let o of F(s))!A.call(i,o)&&o!==u&&x(i,o,{get:()=>s[o],enumerable:!(a=q(s,o))||a.enumerable});return i};var X=(i,s,u)=>(u=i!=null?D(G(i)):{},J(s||!i||!i.__esModule?x(u,"default",{value:i,enumerable:!0}):u,i));var C=K((V,T)=>{(function(){var i=typeof window<"u"&&window===this?this:typeof global<"u"&&global!=null?global:this,s=typeof Object.defineProperties=="function"?Object.defineProperty:function(t,n,e){t!=Array.prototype&&t!=Object.prototype&&(t[n]=e.value)};function u(){u=function(){},i.Symbol||(i.Symbol=o)}var a=0;function o(t){return"jscomp_symbol_"+(t||"")+a++}function d(){u();var t=i.Symbol.iterator;t||(t=i.Symbol.iterator=i.Symbol("iterator")),typeof Array.prototype[t]!="function"&&s(Array.prototype,t,{configurable:!0,writable:!0,value:function(){return y(this)}}),d=function(){}}function y(t){var n=0;return v(function(){return n<t.length?{done:!1,value:t[n++]}:{done:!0}})}function v(t){return d(),t={next:t},t[i.Symbol.iterator]=function(){return this},t}function l(t){d();var n=t[Symbol.iterator];return n?n.call(t):y(t)}function O(t){if(!(t instanceof Array)){t=l(t);for(var n,e=[];!(n=t.next()).done;)e.push(n.value);t=e}return t}var L=0;function B(t,n){var e=XMLHttpRequest.prototype.send,r=L++;XMLHttpRequest.prototype.send=function(p){for(var f=[],c=0;c<arguments.length;++c)f[c-0]=arguments[c];var b=this;return t(r),this.addEventListener("readystatechange",function(){b.readyState===4&&n(r)}),e.apply(this,f)}}function k(t,n){var e=fetch;fetch=function(r){for(var p=[],f=0;f<arguments.length;++f)p[f-0]=arguments[f];return new Promise(function(c,b){var I=L++;t(I),e.apply(null,[].concat(O(p))).then(function(g){n(I),c(g)},function(g){n(g),b(g)})})}}var S="img script iframe link audio video source".split(" ");function _(t,n){t=l(t);for(var e=t.next();!e.done;e=t.next())if(e=e.value,n.includes(e.nodeName.toLowerCase())||_(e.children,n))return!0;return!1}function R(t){var n=new MutationObserver(function(e){e=l(e);for(var r=e.next();!r.done;r=e.next())r=r.value,(r.type=="childList"&&_(r.addedNodes,S)||r.type=="attributes"&&S.includes(r.target.tagName.toLowerCase()))&&t(r)});return n.observe(document,{attributes:!0,childList:!0,subtree:!0,attributeFilter:["href","src"]}),n}function w(t,n){if(2<t.length)return performance.now();var e=[];n=l(n);for(var r=n.next();!r.done;r=n.next())r=r.value,e.push({timestamp:r.start,type:"requestStart"}),e.push({timestamp:r.end,type:"requestEnd"});for(n=l(t),r=n.next();!r.done;r=n.next())e.push({timestamp:r.value,type:"requestStart"});for(e.sort(function(p,f){return p.timestamp-f.timestamp}),t=t.length,n=e.length-1;0<=n;n--)switch(r=e[n],r.type){case"requestStart":t--;break;case"requestEnd":if(t++,2<t)return r.timestamp;break;default:throw Error("Internal Error: This should never happen")}return 0}function m(t){t=t||{},this.w=!!t.useMutationObserver,this.u=t.minValue||null,t=window.__tti&&window.__tti.e;var n=window.__tti&&window.__tti.o;this.a=t?t.map(function(e){return{start:e.startTime,end:e.startTime+e.duration}}):[],n&&n.disconnect(),this.c=[],this.f=new Map,this.j=null,this.v=-1/0,this.i=!1,this.h=this.b=this.s=null,B(this.m.bind(this),this.l.bind(this)),k(this.m.bind(this),this.l.bind(this)),j(this),this.w&&(this.h=R(this.A.bind(this)))}m.prototype.getFirstConsistentlyInteractive=function(){var t=this;return new Promise(function(n){t.s=n,document.readyState=="complete"?M(t):window.addEventListener("load",function(){M(t)})})};function M(t){t.i=!0;var n=0<t.a.length?t.a[t.a.length-1].end:0,e=w(t.g,t.c);h(t,Math.max(e+5e3,n))}function h(t,n){!t.i||t.v>n||(clearTimeout(t.j),t.j=setTimeout(function(){var p=performance.timing.navigationStart,e=w(t.g,t.c),r=window.performance.getEntriesByName("first-contentful-paint")[0],p=(r?r.startTime:void 0)||performance.timing.domContentLoadedEventEnd-p;t.u?r=t.u:performance.timing.domContentLoadedEventEnd?(r=performance.timing,r=r.domContentLoadedEventEnd-r.navigationStart):r=null;var f=performance.now();r===null&&h(t,Math.max(e+5e3,f+1e3));var c=t.a;5e3>f-e?e=null:(e=c.length?c[c.length-1].end:p,e=5e3>f-e?null:Math.max(e,r)),e&&(t.s(e),clearTimeout(t.j),t.i=!1,t.b&&t.b.disconnect(),t.h&&t.h.disconnect()),h(t,performance.now()+1e3)},n-performance.now()),t.v=n)}function j(t){t.b=new PerformanceObserver(function(n){n=l(n.getEntries());for(var e=n.next();!e.done;e=n.next())if(e=e.value,e.entryType==="resource"&&(t.c.push({start:e.fetchStart,end:e.responseEnd}),h(t,w(t.g,t.c)+5e3)),e.entryType==="longtask"){var r=e.startTime+e.duration;t.a.push({start:e.startTime,end:r}),h(t,r+5e3)}}),t.b.observe({type:"longtask",buffered:!0}),t.b.observe({type:"resource",buffered:!0})}m.prototype.m=function(t){this.f.set(t,performance.now())},m.prototype.l=function(t){this.f.delete(t)},m.prototype.A=function(){h(this,performance.now()+5e3)},i.Object.defineProperties(m.prototype,{g:{configurable:!0,enumerable:!0,get:function(){return[].concat(O(this.f.values()))}}});var E={getFirstConsistentlyInteractive:function(t){return t=t||{},"PerformanceLongTaskTiming"in window?new m(t).getFirstConsistentlyInteractive():Promise.resolve(null)}};typeof T<"u"&&T.exports?T.exports=E:typeof define=="function"&&define.amd?define("ttiPolyfill",[],function(){return E}):window.ttiPolyfill=E})()});var P=X(C());function H(i,s=[],u){return i<=u?0:s.reduce((a,o)=>{if(o.startTime<u&&o.startTime+o.duration>=u){let d=o.duration-(u-o.startTime);return d>=50&&(a+=d-50),a}return o.startTime<i&&o.startTime+o.duration>i&&i-o.startTime>=50?(a+=i-o.startTime-50,a):(o.startTime<u||o.startTime>i||o.duration<=50||(a+=o.duration-50),a)},0)}async function N(){let i=[],s=new PerformanceObserver(v=>{v.getEntries().forEach(l=>{i.push(l)})});s.observe({type:"longtask",buffered:!0});let u=0,a=new PerformanceObserver(v=>{let l=v.getEntries();u=l[l.length-1].startTime});a.observe({type:"largest-contentful-paint",buffered:!0});let o=await(0,P.getFirstConsistentlyInteractive)({useMutationObserver:!1});s.disconnect();let d=performance.getEntriesByName("first-contentful-paint")[0]?.startTime??0,y=H(o,i,d);return a.disconnect(),{tbt:y,tti:o,fcp:d,lcp:u,longTasks:i}}N().then(({tbt:i,lcp:s,longTasks:u})=>{let a=JSON.stringify({tbt:Math.round(i),lcp:Math.round(s),longTasksNum:u.length});ddgPerfMetrics.onMetrics(location.href+" "+a),window.alert("PERF METRICS: "+a)});})();
        """.trimIndent()
    }
}
