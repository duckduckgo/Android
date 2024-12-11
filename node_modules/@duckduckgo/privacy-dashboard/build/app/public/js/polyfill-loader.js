/**
 * https://bugs.chromium.org/p/v8/issues/detail?id=10682
 */
function hasIntlGetCanonicalLocalesBug() {
    try {
        return new Intl.Locale('und-x-private').toString() === 'x-private';
    } catch (e) {
        return true;
    }
}
function shouldPolyfill() {
    return !('Locale' in Intl) || hasIntlGetCanonicalLocalesBug();
}
/**
 * Load the Intl.Locale polyfill if needed
 */
if (shouldPolyfill()) {
    const script = document.createElement('script');
    script.src = '../public/js/polyfills.js';
    document.head.appendChild(script);
}
