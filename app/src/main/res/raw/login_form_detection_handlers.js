// Do not wrap this code into an anonymous function. Used in JavaScriptDetector.kt

window.addEventListener("DOMContentLoaded", function(event) {
    LoginDetection.log("Adding to DOM");
    setTimeout(scanForForms, 1000);
});

window.addEventListener("click", scanForForms);
window.addEventListener("beforeunload", scanForForms);

window.addEventListener("submit", submitHandler);

LoginDetection.log("installing loginDetection.js - OUT");