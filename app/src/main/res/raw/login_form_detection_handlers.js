// This code must be wrapped in an anonymous function which is done in JavaScriptDetector.kt to allow for dynamic changes before wrapping.

window.addEventListener("DOMContentLoaded", function(event) {
    LoginDetection.log("Adding to DOM");
    setTimeout(scanForForms, 1000);
});

window.addEventListener("click", scanForForms);
window.addEventListener("beforeunload", scanForForms);

window.addEventListener("submit", submitHandler);

LoginDetection.log("installing loginDetection.js - OUT");