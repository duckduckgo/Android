LoginDetection.showToast("installing loginDetection.js - IN");

function loginFormDetected() {
    try {
        LoginDetection.loginDetected("login detected");
    } catch (error) {}
}

function inputVisible(input) {
    return !(input.offsetWidth === 0 && input.offsetHeight === 0) && !input.ariaHidden && !input.hidden && input.value !== "";
}

function checkIsLoginForm(form) {
    LoginDetection.showToast("checking form " + form);

    var inputs = form.getElementsByTagName("input");
    if (!inputs) {
        return;
    }

    for (var i = 0; i < inputs.length; i++) {
        var input = inputs.item(i);
        if (input.type == "password" && inputVisible(input)) {
            LoginDetection.showToast("found password in form " + form);
            loginFormDetected();
            return true;
        }
    }

    LoginDetection.showToast("no password field in form " + form);
    return false;
}

function submitHandler(event) {
    checkIsLoginForm(event.target);
}

function scanForForms() {
    LoginDetection.showToast("Scanning for forms");

    var forms = document.forms;
    if (!forms || forms.length === 0) {
        LoginDetection.showToast("No forms found");
        return;
    }

    for (var i = 0; i < forms.length; i++) {
        var form = forms[i];
        form.removeEventListener("submit", submitHandler);
        form.addEventListener("submit", submitHandler);
        LoginDetection.showToast("adding form handler " + i);
    }
}

function scanForPasswordField() {
    LoginDetection.showToast("Scanning for password");

    var forms = document.forms;
    if (!forms || forms.length === 0) {
        LoginDetection.showToast("No forms found");
        return;
    }

    for (var i = 0; i < forms.length; i++) {
        var form = forms[i];
        var found = checkIsLoginForm(form);
        if (found) {
            return found;
        }
    }
}

window.addEventListener("DOMContentLoaded", function(event) {
    LoginDetection.showToast("Adding to DOM");
    setTimeout(scanForForms, 1000);
});

window.addEventListener("click", scanForForms);
window.addEventListener("beforeunload", scanForForms);

window.addEventListener("submit", submitHandler);

LoginDetection.showToast("installing loginDetection.js - OUT");