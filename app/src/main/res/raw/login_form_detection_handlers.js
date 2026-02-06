// This code must be wrapped in an anonymous function which is done in JavaScriptDetector.kt to allow for dynamic changes before wrapping.

window.addEventListener("DOMContentLoaded", function(event) {
    try { LoginDetection.log("Adding to DOM"); } catch (error) { /* Expected: LoginDetection bridge may not be attached */ }
    setTimeout(scanForForms, 1000);
});

window.addEventListener("click", scanForForms);
window.addEventListener("beforeunload", scanForForms);

window.addEventListener("submit", submitHandler);

try { LoginDetection.log("installing loginDetection.js - OUT"); } catch (error) { /* Expected: LoginDetection bridge may not be attached */ }