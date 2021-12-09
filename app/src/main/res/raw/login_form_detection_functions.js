// This code must be wrapped in an anonymous function which is done in JavaScriptDetector.kt to allow for dynamic changes before wrapping.
// JS utility functions used by JsLoginDetector in order to detect login attempts and to ask users to fireproof a website

function loginAttemptDetected() {
    try {
        LoginDetection.log("Possible login attempt detected");
        LoginDetection.loginDetected();
    } catch (error) {}
}

function inputVisible(input) {
    return !(input.offsetWidth === 0 && input.offsetHeight === 0) && !input.hidden && input.value !== "";
}

function validatePasswordField(passwords) {
    for (var i = 0; i < passwords.length; i++) {
        var password = passwords[i];
        var found = inputVisible(password);
        if (found) {
            loginAttemptDetected();
            return found;
        }
    }
}

function checkIsLoginForm(form) {
    LoginDetection.log("checking form " + form);

    var inputs = form.getElementsByTagName("input");
    if (!inputs) {
        return;
    }

    for (var i = 0; i < inputs.length; i++) {
        var input = inputs.item(i);
        if (input.type == "password" && inputVisible(input)) {
            loginAttemptDetected();
            return true;
        }
    }

    LoginDetection.log("No password field in form " + form);
    return false;
}

function scanPasswordFieldsInIFrame() {
    LoginDetection.log("Scanning for iframes");
    var iframes = document.querySelectorAll('iframe');
    for (var i = 0; i < iframes.length; i++) {
        var iframeDoc = iframes[i].contentWindow.document;
        passwords = iframeDoc.querySelectorAll('input[type=password]');
        var found = validatePasswordField(passwords);
        if (found) {
            return found;
        }
    }
    return false;
}

function submitHandler(event) {
    checkIsLoginForm(event.target);
}

function scanForForms() {
    LoginDetection.log("Scanning for forms");

    var forms = document.forms;
    if (!forms || forms.length === 0) {
        LoginDetection.log("No forms found");
        return;
    }

    for (var i = 0; i < forms.length; i++) {
        var form = forms[i];
        form.removeEventListener("submit", submitHandler);
        form.addEventListener("submit", submitHandler);
        LoginDetection.log("adding form handler " + i);
    }
}

function scanForPasswordField() {
    LoginDetection.log("Scanning DOM for password fields");
    var passwords = document.querySelectorAll('input[type=password]');
    if (passwords.length === 0) {
        var found = scanPasswordFieldsInIFrame()
        if (!found) {
            LoginDetection.log("No password fields found");
        }
        return found;
    }
    return validatePasswordField(passwords);
}