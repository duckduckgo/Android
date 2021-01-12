// This code must be wrapped in an anonymous function which is done in JavaScriptDetector.kt to allow for dynamic changes before wrapping.

function loginFormDetected() {
    try {
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
            LoginDetection.log("password found!");
            loginFormDetected();
            return found;
        }
    }
}

function checkIsLoginForm(form) {
    LoginDetection.log("checking form " + form);

    var inputs = form.getElementsByTagName("input");
    LoginDetection.log("checking input " + inputs);
    if (!inputs) {
        return;
    }

    for (var i = 0; i < inputs.length; i++) {
        var input = inputs.item(i);
        LoginDetection.log("checking input " + input.type);
        if (input.type == "password" && inputVisible(input)) {
            LoginDetection.log("found password in form " + form);
            loginFormDetected();
            return true;
        }
    }

    LoginDetection.log("no password field in form " + form);
    return false;
}

function scanPasswordFieldsInIFrame() {
    LoginDetection.log("scanning iframes");
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
    LoginDetection.log("Scanning for password");
    var passwords = document.querySelectorAll('input[type=password]');
    if (passwords.length === 0) {
        var found = scanPasswordFieldsInIFrame()
        if (!found) {
            LoginDetection.log("No password found");
        }
        return found;
    }
    return validatePasswordField(passwords);
}