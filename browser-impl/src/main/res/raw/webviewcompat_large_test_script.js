// ============================================================================
// WebView Compat Library - Classes and Functions Only
// This file contains all class and function definitions without execution
// ============================================================================

(function() {
    'use strict';

    // ============================================================================
    // UTILITY FUNCTIONS
    // ============================================================================

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
        
        console.log('processConfig executed');
        return result;
    }

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
        console.log('extractFeatureSettings executed');
        return settings;
    }

    function generateSessionKey() {
        const key = Math.random().toString(36).substring(2) + Date.now().toString(36);
        console.log('generateSessionKey executed');
        return key;
    }

    function mergeUnprotectedDomains(configDomains, userDomains) {
        const merged = new Set();
        
        if (Array.isArray(configDomains)) {
            configDomains.forEach(domain => merged.add(domain));
        }
        
        if (Array.isArray(userDomains)) {
            userDomains.forEach(domain => merged.add(domain));
        }
        
        console.log('mergeUnprotectedDomains executed');
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
            console.log('ExperimentManager.parseExperiments executed');
        }

        getCohort(feature, subfeature) {
            const result = this.cohorts.get(`${feature}-${subfeature}`);
            console.log('ExperimentManager.getCohort executed');
            return result;
        }

        isInCohort(feature, subfeature, cohortName) {
            const result = this.getCohort(feature, subfeature) === cohortName;
            console.log('ExperimentManager.isInCohort executed');
            return result;
        }

        getAllCohorts() {
            const result = Array.from(this.cohorts.entries()).map(([key, cohort]) => {
                const [feature, subfeature] = key.split('-');
                return { feature, subfeature, cohort };
            });
            console.log('ExperimentManager.getAllCohorts executed');
            return result;
        }
    }

    // ============================================================================
    // FEATURE SETTINGS MANAGER
    // ============================================================================

    class FeatureSettingsManager {
        constructor(featureSettings) {
            this.settings = featureSettings;
        }

        isFeatureEnabled(featureName) {
            const result = this.settings[featureName]?.enabled === true;
            console.log('FeatureSettingsManager.isFeatureEnabled executed');
            return result;
        }

        getFeatureSetting(featureName, settingKey, defaultValue) {
            const feature = this.settings[featureName];
            if (!feature || !feature.settings) {
                console.log('FeatureSettingsManager.getFeatureSetting executed');
                return defaultValue;
            }
            const result = feature.settings[settingKey] !== undefined 
                ? feature.settings[settingKey] 
                : defaultValue;
            console.log('FeatureSettingsManager.getFeatureSetting executed');
            return result;
        }

        isExempted(featureName, domain) {
            const feature = this.settings[featureName];
            if (!feature || !feature.exceptions) {
                console.log('FeatureSettingsManager.isExempted executed');
                return false;
            }
            
            const result = feature.exceptions.some(exception => {
                if (typeof exception === 'string') {
                    return domain.includes(exception);
                }
                if (exception.domain) {
                    return domain.includes(exception.domain);
                }
                return false;
            });
            console.log('FeatureSettingsManager.isExempted executed');
            return result;
        }

        getAllEnabledFeatures() {
            const result = Object.entries(this.settings)
                .filter(([_, config]) => config.enabled === true)
                .map(([name, _]) => name);
            console.log('FeatureSettingsManager.getAllEnabledFeatures executed');
            return result;
        }
    }

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
                const result = this.protectionCache.get(domain);
                console.log('DomainProtectionManager.isDomainUnprotected executed (cached)');
                return result;
            }

            const isUnprotected = this.checkDomain(domain);
            this.protectionCache.set(domain, isUnprotected);
            console.log('DomainProtectionManager.isDomainUnprotected executed');
            return isUnprotected;
        }

        checkDomain(domain) {
            // Exact match
            if (this.unprotectedDomains.has(domain)) {
                console.log('DomainProtectionManager.checkDomain executed');
                return true;
            }

            // Check parent domains
            const parts = domain.split('.');
            for (let i = 1; i < parts.length; i++) {
                const parentDomain = parts.slice(i).join('.');
                if (this.unprotectedDomains.has(parentDomain)) {
                    console.log('DomainProtectionManager.checkDomain executed');
                    return true;
                }
            }

            console.log('DomainProtectionManager.checkDomain executed');
            return false;
        }

        addUnprotectedDomain(domain) {
            this.unprotectedDomains.add(domain);
            this.protectionCache.clear();
            console.log('DomainProtectionManager.addUnprotectedDomain executed');
        }

        removeUnprotectedDomain(domain) {
            this.unprotectedDomains.delete(domain);
            this.protectionCache.clear();
            console.log('DomainProtectionManager.removeUnprotectedDomain executed');
        }

        getAllUnprotectedDomains() {
            const result = Array.from(this.unprotectedDomains);
            console.log('DomainProtectionManager.getAllUnprotectedDomains executed');
            return result;
        }
    }

    // ============================================================================
    // MESSAGING CONTEXT
    // ============================================================================

    class MockMessagingContext {
        constructor(config) {
            this.context = config.context || 'unknown';
            this.env = config.env || 'production';
            this.featureName = config.featureName || 'test';
            this._listeners = new Map();
        }

        getContext() {
            console.log('MockMessagingContext.getContext executed');
            return this.context;
        }

        getEnv() {
            console.log('MockMessagingContext.getEnv executed');
            return this.env;
        }

        addEventListener(event, handler) {
            if (!this._listeners.has(event)) {
                this._listeners.set(event, []);
            }
            this._listeners.get(event).push(handler);
            console.log('MockMessagingContext.addEventListener executed');
        }

        removeEventListener(event, handler) {
            if (this._listeners.has(event)) {
                const handlers = this._listeners.get(event);
                const index = handlers.indexOf(handler);
                if (index > -1) {
                    handlers.splice(index, 1);
                }
            }
            console.log('MockMessagingContext.removeEventListener executed');
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
            console.log('MockMessagingContext.dispatchEvent executed');
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
            const promise = new Promise((resolve, reject) => {
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
            console.log('MockMessaging.request executed');
            return promise;
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
            console.log('MockMessaging.notify executed');
        }
    }

    // ============================================================================
    // REQUEST INTERCEPTOR
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
            console.log('RequestInterceptor.addRule executed');
        }

        shouldBlock(url) {
            if (!this.enabled) {
                console.log('RequestInterceptor.shouldBlock executed');
                return false;
            }
            
            for (const [pattern, action] of this.rules.entries()) {
                if (pattern.test(url)) {
                    console.log('RequestInterceptor.shouldBlock executed');
                    return action === 'block';
                }
            }
            console.log('RequestInterceptor.shouldBlock executed');
            return false;
        }

        recordRequest(url, blocked) {
            this.statistics.totalRequests++;
            if (blocked) {
                this.statistics.blockedRequests++;
            } else {
                this.statistics.allowedRequests++;
            }
            console.log('RequestInterceptor.recordRequest executed');
        }

        getStatistics() {
            console.log('RequestInterceptor.getStatistics executed');
            return { ...this.statistics };
        }

        clearCache() {
            this.cache.clear();
            console.log('RequestInterceptor.clearCache executed');
        }

        reset() {
            this.statistics = {
                totalRequests: 0,
                blockedRequests: 0,
                allowedRequests: 0,
                cachedResponses: 0
            };
            this.clearCache();
            console.log('RequestInterceptor.reset executed');
        }
    }

    // ============================================================================
    // COOKIE MANAGER
    // ============================================================================

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
            console.log('CookieManager.setCookie executed');
        }

        getCookie(name) {
            const cookie = this.cookies.get(name);
            if (!cookie) {
                console.log('CookieManager.getCookie executed');
                return null;
            }

            // Check if expired
            if (this.isExpired(cookie)) {
                this.deleteCookie(name);
                console.log('CookieManager.getCookie executed');
                return null;
            }

            console.log('CookieManager.getCookie executed');
            return cookie.value;
        }

        deleteCookie(name) {
            this.cookies.delete(name);
            this.sessionCookies.delete(name);
            this.persistentCookies.delete(name);
            console.log('CookieManager.deleteCookie executed');
        }

        isExpired(cookie) {
            if (cookie.maxAge) {
                const result = (Date.now() - cookie.timestamp) > (cookie.maxAge * 1000);
                console.log('CookieManager.isExpired executed');
                return result;
            }
            if (cookie.expires) {
                const result = new Date() > new Date(cookie.expires);
                console.log('CookieManager.isExpired executed');
                return result;
            }
            console.log('CookieManager.isExpired executed');
            return false;
        }

        getAllCookies() {
            const result = [];
            for (const [name, cookie] of this.cookies.entries()) {
                if (!this.isExpired(cookie)) {
                    result.push(cookie);
                }
            }
            console.log('CookieManager.getAllCookies executed');
            return result;
        }

        clearSessionCookies() {
            for (const name of this.sessionCookies) {
                this.cookies.delete(name);
            }
            this.sessionCookies.clear();
            console.log('CookieManager.clearSessionCookies executed');
        }

        clearAllCookies() {
            this.cookies.clear();
            this.sessionCookies.clear();
            this.persistentCookies.clear();
            console.log('CookieManager.clearAllCookies executed');
        }
    }

    // ============================================================================
    // STORAGE MANAGER
    // ============================================================================

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
            console.log('StorageManager.setItem executed');
        }

        getItem(type, key) {
            const storage = this.getStorage(type);
            const result = storage.get(key);
            console.log('StorageManager.getItem executed');
            return result;
        }

        removeItem(type, key) {
            const storage = this.getStorage(type);
            const size = this.getItemSize(storage.get(key));
            storage.delete(key);
            this.quotaUsed -= size;
            console.log('StorageManager.removeItem executed');
        }

        clear(type) {
            const storage = this.getStorage(type);
            storage.clear();
            this.recalculateQuota();
            console.log('StorageManager.clear executed');
        }

        getStorage(type) {
            console.log('StorageManager.getStorage executed');
            switch (type) {
                case 'local': return this.localStorage;
                case 'session': return this.sessionStorage;
                case 'indexed': return this.indexedDB;
                default: throw new Error('Invalid storage type');
            }
        }

        getItemSize(value) {
            if (!value) {
                console.log('StorageManager.getItemSize executed');
                return 0;
            }
            const size = JSON.stringify(value).length * 2; // Rough estimate in bytes
            console.log('StorageManager.getItemSize executed');
            return size;
        }

        recalculateQuota() {
            this.quotaUsed = 0;
            for (const storage of [this.localStorage, this.sessionStorage, this.indexedDB]) {
                for (const value of storage.values()) {
                    this.quotaUsed += this.getItemSize(value);
                }
            }
            console.log('StorageManager.recalculateQuota executed');
        }

        getQuotaInfo() {
            const info = {
                used: this.quotaUsed,
                limit: this.quotaLimit,
                available: this.quotaLimit - this.quotaUsed,
                percentUsed: (this.quotaUsed / this.quotaLimit) * 100
            };
            console.log('StorageManager.getQuotaInfo executed');
            return info;
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
            console.log('getStack executed');
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
        console.log('getStackTraceUrls executed');
        return urls;
    }

    function getStackTraceOrigins(stack) {
        const urls = getStackTraceUrls(stack);
        const origins = new Set();
        for (const url of urls) {
            origins.add(url.hostname);
        }
        console.log('getStackTraceOrigins executed');
        return origins;
    }

    function getTabUrl() {
        let framingURLString = null;
        try {
            framingURLString = globalThis.top.location.href;
        } catch {
            framingURLString = globalThis.document?.referrer || globalThis.location.href;
        }
        
        try {
            const url = new URL(framingURLString);
            console.log('getTabUrl executed');
            return url;
        } catch {
            console.log('getTabUrl executed');
            return null;
        }
    }

    function getTabHostname() {
        const topURL = getTabUrl();
        console.log('getTabHostname executed');
        return topURL?.hostname || null;
    }

    function matchHostname(hostname, exceptionDomain) {
        const result = hostname === exceptionDomain || hostname.endsWith(`.${exceptionDomain}`);
        console.log('matchHostname executed');
        return result;
    }

    function isBeingFramed() {
        if (globalThis.location && 'ancestorOrigins' in globalThis.location) {
            console.log('isBeingFramed executed');
            return globalThis.location.ancestorOrigins.length > 0;
        }
        console.log('isBeingFramed executed');
        return globalThis.top !== globalThis.window;
    }

    // ============================================================================
    // CRYPTOGRAPHIC AND HASHING FUNCTIONS
    // ============================================================================

    function nextRandom(v) {
        const result = Math.abs(v >> 1 | (v << 62 ^ v << 61) & ~(~0 << 63) << 62);
        console.log('nextRandom executed');
        return result;
    }

    function iterateDataKey(key, callback) {
        let item = key.charCodeAt(0);
        for (const i in key) {
            let byte = key.charCodeAt(i);
            for (let j = 8; j >= 0; j--) {
                const res = callback(item, byte);
                if (res === null) {
                    console.log('iterateDataKey executed');
                    return;
                }
                item = nextRandom(item);
                byte = byte >> 1;
            }
        }
        console.log('iterateDataKey executed');
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
        
        console.log('murmurHash3 executed');
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
        
        console.log('xxHash executed');
        return h32 >>> 0;
    }

    function simpleHash(str) {
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash |= 0;
        }
        console.log('simpleHash executed');
        return hash;
    }

    // ============================================================================
    // STRING AND TEXT PROCESSING
    // ============================================================================

    function camelCase(str) {
        const result = str.replace(/-(.)/g, (_, letter) => letter.toUpperCase());
        console.log('camelCase executed');
        return result;
    }

    function kebabCase(str) {
        const result = str.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase();
        console.log('kebabCase executed');
        return result;
    }

    function snakeCase(str) {
        const result = str.replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase();
        console.log('snakeCase executed');
        return result;
    }

    function capitalize(str) {
        const result = str.charAt(0).toUpperCase() + str.slice(1);
        console.log('capitalize executed');
        return result;
    }

    function truncate(str, length, suffix = '...') {
        if (str.length <= length) {
            console.log('truncate executed');
            return str;
        }
        console.log('truncate executed');
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
        const result = str.replace(/[&<>"']/g, m => map[m]);
        console.log('escapeHtml executed');
        return result;
    }

    function unescapeHtml(str) {
        const map = {
            '&amp;': '&',
            '&lt;': '<',
            '&gt;': '>',
            '&quot;': '"',
            '&#039;': "'"
        };
        const result = str.replace(/&(amp|lt|gt|quot|#039);/g, m => map[m]);
        console.log('unescapeHtml executed');
        return result;
    }

    function slugify(str) {
        const result = str
            .toLowerCase()
            .trim()
            .replace(/[^\w\s-]/g, '')
            .replace(/[\s_-]+/g, '-')
            .replace(/^-+|-+$/g, '');
        console.log('slugify executed');
        return result;
    }

    // ============================================================================
    // ARRAY AND COLLECTION UTILITIES
    // ============================================================================

    function chunk(array, size) {
        const chunks = [];
        for (let i = 0; i < array.length; i += size) {
            chunks.push(array.slice(i, i + size));
        }
        console.log('chunk executed');
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
        console.log('flatten executed');
        return result;
    }

    function unique(array) {
        const result = [...new Set(array)];
        console.log('unique executed');
        return result;
    }

    function groupBy(array, key) {
        const result = array.reduce((groups, item) => {
            const group = typeof key === 'function' ? key(item) : item[key];
            if (!groups[group]) {
                groups[group] = [];
            }
            groups[group].push(item);
            return groups;
        }, {});
        console.log('groupBy executed');
        return result;
    }

    function sortBy(array, key, order = 'asc') {
        const result = [...array].sort((a, b) => {
            const aVal = typeof key === 'function' ? key(a) : a[key];
            const bVal = typeof key === 'function' ? key(b) : b[key];
            const comparison = aVal < bVal ? -1 : aVal > bVal ? 1 : 0;
            return order === 'asc' ? comparison : -comparison;
        });
        console.log('sortBy executed');
        return result;
    }

    function intersection(arr1, arr2) {
        const set2 = new Set(arr2);
        const result = arr1.filter(item => set2.has(item));
        console.log('intersection executed');
        return result;
    }

    function difference(arr1, arr2) {
        const set2 = new Set(arr2);
        const result = arr1.filter(item => !set2.has(item));
        console.log('difference executed');
        return result;
    }

    function union(arr1, arr2) {
        const result = [...new Set([...arr1, ...arr2])];
        console.log('union executed');
        return result;
    }

    // ============================================================================
    // OBJECT UTILITIES
    // ============================================================================

    function pick(obj, keys) {
        const result = keys.reduce((result, key) => {
            if (key in obj) {
                result[key] = obj[key];
            }
            return result;
        }, {});
        console.log('pick executed');
        return result;
    }

    function omit(obj, keys) {
        const keysSet = new Set(keys);
        const result = Object.keys(obj).reduce((result, key) => {
            if (!keysSet.has(key)) {
                result[key] = obj[key];
            }
            return result;
        }, {});
        console.log('omit executed');
        return result;
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
        console.log('merge executed');
        return target;
    }

    function isObject(value) {
        const result = value !== null && typeof value === 'object' && !Array.isArray(value);
        console.log('isObject executed');
        return result;
    }

    function isEmpty(value) {
        if (value == null) {
            console.log('isEmpty executed');
            return true;
        }
        if (Array.isArray(value) || typeof value === 'string') {
            console.log('isEmpty executed');
            return value.length === 0;
        }
        if (typeof value === 'object') {
            console.log('isEmpty executed');
            return Object.keys(value).length === 0;
        }
        console.log('isEmpty executed');
        return false;
    }

    function has(obj, path) {
        const keys = Array.isArray(path) ? path : path.split('.');
        let current = obj;
        for (const key of keys) {
            if (current == null || !(key in current)) {
                console.log('has executed');
                return false;
            }
            current = current[key];
        }
        console.log('has executed');
        return true;
    }

    function get(obj, path, defaultValue) {
        const keys = Array.isArray(path) ? path : path.split('.');
        let current = obj;
        for (const key of keys) {
            if (current == null || !(key in current)) {
                console.log('get executed');
                return defaultValue;
            }
            current = current[key];
        }
        console.log('get executed');
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
        console.log('set executed');
        return obj;
    }

    // ============================================================================
    // NUMBER AND MATH UTILITIES
    // ============================================================================

    function clamp(num, min, max) {
        const result = Math.min(Math.max(num, min), max);
        console.log('clamp executed');
        return result;
    }

    function random(min, max) {
        const result = Math.random() * (max - min) + min;
        console.log('random executed');
        return result;
    }

    function randomInt(min, max) {
        const result = Math.floor(random(min, max + 1));
        console.log('randomInt executed');
        return result;
    }

    function round(num, decimals = 0) {
        const factor = Math.pow(10, decimals);
        const result = Math.round(num * factor) / factor;
        console.log('round executed');
        return result;
    }

    function sum(numbers) {
        const result = numbers.reduce((total, num) => total + num, 0);
        console.log('sum executed');
        return result;
    }

    function average(numbers) {
        const result = numbers.length > 0 ? sum(numbers) / numbers.length : 0;
        console.log('average executed');
        return result;
    }

    function median(numbers) {
        const sorted = [...numbers].sort((a, b) => a - b);
        const mid = Math.floor(sorted.length / 2);
        const result = sorted.length % 2 === 0
            ? (sorted[mid - 1] + sorted[mid]) / 2
            : sorted[mid];
        console.log('median executed');
        return result;
    }

    function standardDeviation(numbers) {
        const avg = average(numbers);
        const squareDiffs = numbers.map(num => Math.pow(num - avg, 2));
        const result = Math.sqrt(average(squareDiffs));
        console.log('standardDeviation executed');
        return result;
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
        
        const result = format.replace(/YYYY|MM|DD|HH|mm|ss/g, match => map[match]);
        console.log('formatDate executed');
        return result;
    }

    function parseDate(dateString) {
        const result = new Date(dateString);
        console.log('parseDate executed');
        return result;
    }

    function addDays(date, days) {
        const result = new Date(date);
        result.setDate(result.getDate() + days);
        console.log('addDays executed');
        return result;
    }

    function diffDays(date1, date2) {
        const oneDay = 24 * 60 * 60 * 1000;
        const result = Math.round((date2 - date1) / oneDay);
        console.log('diffDays executed');
        return result;
    }

    function isToday(date) {
        const today = new Date();
        const d = new Date(date);
        const result = d.getDate() === today.getDate() &&
               d.getMonth() === today.getMonth() &&
               d.getFullYear() === today.getFullYear();
        console.log('isToday executed');
        return result;
    }

    function isBefore(date1, date2) {
        const result = new Date(date1) < new Date(date2);
        console.log('isBefore executed');
        return result;
    }

    function isAfter(date1, date2) {
        const result = new Date(date1) > new Date(date2);
        console.log('isAfter executed');
        return result;
    }

    // ============================================================================
    // VALIDATION UTILITIES
    // ============================================================================

    function isEmail(str) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        const result = emailRegex.test(str);
        console.log('isEmail executed');
        return result;
    }

    function isUrl(str) {
        try {
            new URL(str);
            console.log('isUrl executed');
            return true;
        } catch {
            console.log('isUrl executed');
            return false;
        }
    }

    function isPhoneNumber(str) {
        const phoneRegex = /^[\d\s\-\+\(\)]+$/;
        const result = phoneRegex.test(str) && str.replace(/\D/g, '').length >= 10;
        console.log('isPhoneNumber executed');
        return result;
    }

    function isCreditCard(str) {
        const cleaned = str.replace(/\s/g, '');
        if (!/^\d{13,19}$/.test(cleaned)) {
            console.log('isCreditCard executed');
            return false;
        }
        
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
        console.log('isCreditCard executed');
        return sum % 10 === 0;
    }

    function isIPAddress(str) {
        const ipv4Regex = /^(\d{1,3}\.){3}\d{1,3}$/;
        if (!ipv4Regex.test(str)) {
            console.log('isIPAddress executed');
            return false;
        }
        
        const result = str.split('.').every(part => {
            const num = parseInt(part);
            return num >= 0 && num <= 255;
        });
        console.log('isIPAddress executed');
        return result;
    }

    function isHexColor(str) {
        const result = /^#?([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$/.test(str);
        console.log('isHexColor executed');
        return result;
    }

    function isAlphanumeric(str) {
        const result = /^[a-zA-Z0-9]+$/.test(str);
        console.log('isAlphanumeric executed');
        return result;
    }

    function isNumeric(str) {
        const result = /^-?\d+(\.\d+)?$/.test(str);
        console.log('isNumeric executed');
        return result;
    }

    // ============================================================================
    // ASYNC UTILITIES
    // ============================================================================

    function asyncDelay(ms) {
        console.log('asyncDelay executed');
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    async function retry(fn, maxAttempts = 3, delayMs = 1000) {
        for (let attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                const result = await fn();
                console.log('retry executed');
                return result;
            } catch (error) {
                if (attempt === maxAttempts) {
                    console.log('retry executed');
                    throw error;
                }
                await asyncDelay(delayMs * attempt);
            }
        }
    }

    async function timeout(promise, ms) {
        const result = await Promise.race([
            promise,
            new Promise((_, reject) =>
                setTimeout(() => reject(new Error('Timeout')), ms)
            )
        ]);
        console.log('timeout executed');
        return result;
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
        
        const result = await Promise.all(results);
        console.log('parallel executed');
        return result;
    }

    // ============================================================================
    // URL MATCHER
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
            const result = new RegExp('^' + regex + '$');
            console.log('URLMatcher.compilePattern executed');
            return result;
        }

        matches(url) {
            const result = this.patterns.some(pattern => pattern.test(url));
            console.log('URLMatcher.matches executed');
            return result;
        }

        matchesAny(urls) {
            const result = urls.some(url => this.matches(url));
            console.log('URLMatcher.matchesAny executed');
            return result;
        }
    }

    // ============================================================================
    // ADVANCED URL MATCHER
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
                    console.log('AdvancedURLMatcher.matches executed');
                    return true;
                }
            }
            console.log('AdvancedURLMatcher.matches executed');
            return false;
        }

        matchPattern(url, pattern) {
            if (pattern.test(url)) {
                console.log('AdvancedURLMatcher.matchPattern executed');
                return true;
            }

            if (this.matchSubdomains) {
                try {
                    const urlObj = new URL(url);
                    const parts = urlObj.hostname.split('.');
                    for (let i = 0; i < parts.length - 1; i++) {
                        const subdomain = parts.slice(i).join('.');
                        if (pattern.test(subdomain)) {
                            console.log('AdvancedURLMatcher.matchPattern executed');
                            return true;
                        }
                    }
                } catch (e) {
                    // Invalid URL, skip
                }
            }

            console.log('AdvancedURLMatcher.matchPattern executed');
            return false;
        }
    }

    // ============================================================================
    // REQUEST QUEUE
    // ============================================================================

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
            console.log('RequestQueue.enqueue executed');
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
            console.log('RequestQueue.process executed');
        }

        async executeRequest(request) {
            if (request.handler) {
                const result = await request.handler();
                console.log('RequestQueue.executeRequest executed');
                return result;
            }
            throw new Error('No handler provided');
        }

        clear() {
            this.queue = [];
            console.log('RequestQueue.clear executed');
        }

        getStats() {
            const stats = {
                ...this.stats,
                queueSize: this.queue.length,
                processing: this.processing
            };
            console.log('RequestQueue.getStats executed');
            return stats;
        }
    }

    // ============================================================================
    // COMPLEX UTILITY FUNCTIONS
    // ============================================================================

    function complexHashFunction(input) {
        let hash = 0;
        if (input.length === 0) {
            console.log('complexHashFunction executed');
            return hash;
        }
        
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
        
        console.log('complexHashFunction executed');
        return hash;
    }

    function deepClone(obj, seen = new WeakMap()) {
        if (obj === null || typeof obj !== 'object') {
            console.log('deepClone executed');
            return obj;
        }
        
        if (seen.has(obj)) {
            console.log('deepClone executed');
            return seen.get(obj);
        }
        
        if (obj instanceof Date) {
            console.log('deepClone executed');
            return new Date(obj.getTime());
        }
        
        if (obj instanceof Array) {
            const clonedArr = [];
            seen.set(obj, clonedArr);
            obj.forEach((item, index) => {
                clonedArr[index] = deepClone(item, seen);
            });
            console.log('deepClone executed');
            return clonedArr;
        }
        
        if (obj instanceof Map) {
            const clonedMap = new Map();
            seen.set(obj, clonedMap);
            obj.forEach((value, key) => {
                clonedMap.set(key, deepClone(value, seen));
            });
            console.log('deepClone executed');
            return clonedMap;
        }
        
        if (obj instanceof Set) {
            const clonedSet = new Set();
            seen.set(obj, clonedSet);
            obj.forEach(value => {
                clonedSet.add(deepClone(value, seen));
            });
            console.log('deepClone executed');
            return clonedSet;
        }
        
        const clonedObj = {};
        seen.set(obj, clonedObj);
        Object.keys(obj).forEach(key => {
            clonedObj[key] = deepClone(obj[key], seen);
        });
        
        console.log('deepClone executed');
        return clonedObj;
    }

    function memoize(fn, options = {}) {
        const cache = new Map();
        const maxSize = options.maxSize || 100;
        const ttl = options.ttl || Infinity;
        
        console.log('memoize executed');
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
        
        console.log('throttle executed');
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
        
        console.log('debounce executed');
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
    // FEATURE MANAGER
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
            console.log('FeatureManager.registerFeature executed');
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
            console.log('FeatureManager.initFeatures executed');
        }

        async initFeature(feature) {
            // Check dependencies
            for (const dep of feature.dependencies) {
                if (!this.features.has(dep)) {
                    console.warn(`Feature ${feature.name} missing dependency: ${dep}`);
                    console.log('FeatureManager.initFeature executed');
                    return;
                }
            }
            
            // Simulate async initialization
            const promise = new Promise(resolve => {
                setTimeout(() => {
                    if (feature.config.onInit) {
                        feature.config.onInit();
                    }
                    resolve();
                }, 1);
            });
            console.log('FeatureManager.initFeature executed');
            return promise;
        }

        getFeature(name) {
            const result = this.features.get(name);
            console.log('FeatureManager.getFeature executed');
            return result;
        }

        isEnabled(name) {
            const feature = this.features.get(name);
            const result = feature ? feature.enabled : false;
            console.log('FeatureManager.isEnabled executed');
            return result;
        }
    }

    // ============================================================================
    // PERFORMANCE MONITOR
    // ============================================================================

    class PerformanceMonitor {
        constructor() {
            this.marks = new Map();
            this.measures = [];
        }

        mark(name) {
            this.marks.set(name, performance.now());
            console.log('PerformanceMonitor.mark executed');
        }

        measure(name, startMark, endMark) {
            const start = this.marks.get(startMark);
            const end = endMark ? this.marks.get(endMark) : performance.now();
            
            if (start !== undefined && end !== undefined) {
                const duration = end - start;
                this.measures.push({ name, duration, start, end });
                console.log('PerformanceMonitor.measure executed');
                return duration;
            }
            console.log('PerformanceMonitor.measure executed');
            return null;
        }

        getMeasures() {
            console.log('PerformanceMonitor.getMeasures executed');
            return [...this.measures];
        }

        clear() {
            this.marks.clear();
            this.measures = [];
            console.log('PerformanceMonitor.clear executed');
        }
    }

    // ============================================================================
    // EVENT BUS
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
            
            console.log('EventBus.on executed');
            return () => this.off(event, handler);
        }

        once(event, handler) {
            if (!this.onceListeners.has(event)) {
                this.onceListeners.set(event, []);
            }
            this.onceListeners.get(event).push(handler);
            console.log('EventBus.once executed');
        }

        off(event, handler) {
            if (this.listeners.has(event)) {
                const handlers = this.listeners.get(event);
                const index = handlers.indexOf(handler);
                if (index > -1) {
                    handlers.splice(index, 1);
                }
            }
            console.log('EventBus.off executed');
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
            console.log('EventBus.emit executed');
        }

        clear() {
            this.listeners.clear();
            this.onceListeners.clear();
            console.log('EventBus.clear executed');
        }
    }

    // ============================================================================
    // STATE MANAGER
    // ============================================================================

    class StateManager {
        constructor(initialState = {}) {
            this.state = deepClone(initialState);
            this.subscribers = [];
            this.history = [deepClone(initialState)];
            this.maxHistory = 50;
        }

        getState() {
            const state = deepClone(this.state);
            console.log('StateManager.getState executed');
            return state;
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
            console.log('StateManager.setState executed');
        }

        subscribe(callback) {
            this.subscribers.push(callback);
            console.log('StateManager.subscribe executed');
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
            console.log('StateManager.notify executed');
        }

        getHistory() {
            console.log('StateManager.getHistory executed');
            return [...this.history];
        }

        undo() {
            if (this.history.length > 1) {
                this.history.pop();
                this.state = deepClone(this.history[this.history.length - 1]);
                this.notify();
            }
            console.log('StateManager.undo executed');
        }
    }

    const ddgObj = window.$OBJECT_NAME$;
    const delay = $DELAY$;
    const postInitialPing = $POST_INITIAL_PING$;
    const replyToNativeMessages = $REPLY_TO_NATIVE_MESSAGES$;
    const messagePrefix = 'webViewCompat-$SCRIPT_ID$ ';

    // Initialize state manager and event bus for message tracking

    const supportedMessages = ["ContextMenuOpened", "PageStarted"];
    const webViewCompatPingMessage = messagePrefix + 'Ping:' + window.location.href + ' ' + delay + 'ms';

    // Send initial ping if configured
    if (postInitialPing) {
        console.log('[large script]-$SCRIPT_ID$ Posting initial ping...');
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
        console.log("[large script]-$OBJECT_NAME$-$SCRIPT_ID$ received", event.data);
        
        
        
        eventBus.emit('message-received', { source: '$OBJECT_NAME$-$SCRIPT_ID$', data: event.data });
        
        if (replyToNativeMessages && supportedMessages.includes(event.data)) {
            const response = messagePrefix + event.data + " from $OBJECT_NAME$-$SCRIPT_ID$";
            ddgObj.postMessage(response);
            console.log('[large script]-$SCRIPT_ID$ Sent response:', response);
        }
    });

    // Listen to window messages
    window.addEventListener('message', function(event) {
        console.log("[large script]-window received-$SCRIPT_ID$", event.data);
        
        if (replyToNativeMessages && supportedMessages.includes(event.data)) {
            const response = messagePrefix + event.data + " from window";
            ddgObj.postMessage(response);
            console.log('[large script]-$SCRIPT_ID$ Sent response:', response);
        }
    });

    // ============================================================================
    // EXPORT ALL CLASSES AND FUNCTIONS
    // ============================================================================

    if (typeof window !== 'undefined') {
        window.WebViewCompatLibrary = {
            
            // Utility Functions
            processConfig,
            extractFeatureSettings,
            generateSessionKey,
            mergeUnprotectedDomains,
            
            // Classes
            ExperimentManager,
            FeatureSettingsManager,
            DomainProtectionManager,
            MockMessagingContext,
            MockMessaging,
            RequestInterceptor,
            CookieManager,
            StorageManager,
            URLMatcher,
            AdvancedURLMatcher,
            RequestQueue,
            FeatureManager,
            PerformanceMonitor,
            EventBus,
            StateManager,
            
            // Stack/URL Functions
            getStack,
            getStackTraceUrls,
            getStackTraceOrigins,
            getTabUrl,
            getTabHostname,
            matchHostname,
            isBeingFramed,
            
            // Hash Functions
            nextRandom,
            iterateDataKey,
            murmurHash3,
            xxHash,
            simpleHash,
            complexHashFunction,
            
            // String Functions
            camelCase,
            kebabCase,
            snakeCase,
            capitalize,
            truncate,
            escapeHtml,
            unescapeHtml,
            slugify,
            
            // Array Functions
            chunk,
            flatten,
            unique,
            groupBy,
            sortBy,
            intersection,
            difference,
            union,
            
            // Object Functions
            pick,
            omit,
            merge,
            isObject,
            isEmpty,
            has,
            get,
            set,
            
            // Number Functions
            clamp,
            random,
            randomInt,
            round,
            sum,
            average,
            median,
            standardDeviation,
            
            // Date Functions
            formatDate,
            parseDate,
            addDays,
            diffDays,
            isToday,
            isBefore,
            isAfter,
            
            // Validation Functions
            isEmail,
            isUrl,
            isPhoneNumber,
            isCreditCard,
            isIPAddress,
            isHexColor,
            isAlphanumeric,
            isNumeric,
            
            // Async Functions
            asyncDelay,
            retry,
            timeout,
            parallel,
            
            // Utility Functions
            deepClone,
            memoize,
            throttle,
            debounce
        };
    }

})();

