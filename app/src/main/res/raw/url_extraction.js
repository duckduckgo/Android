// This code must be wrapped in an anonymous function which is done in JsUrlExtractor.kt to allow for dynamic changes before wrapping.

window.addEventListener("DOMContentLoaded", function(event) {
    UrlExtraction.log("DOM content loaded");
    const canonicalLinks = document.querySelectorAll('[rel="canonical"]');
    const url = canonicalLinks.length > 0 ? canonicalLinks[0].href : null;
    UrlExtraction.urlExtracted(url);
});