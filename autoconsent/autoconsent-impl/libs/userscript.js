import AutoConsent from '@duckduckgo/autoconsent';
import * as rules from '@duckduckgo/autoconsent/rules/rules.json';

const autoconsent = new AutoConsent(
    (message) => {
        AutoconsentAndroid.process(JSON.stringify(message));
    },
    null,
    rules,
);
window.autoconsentMessageCallback = (msg) => {
    autoconsent.receiveMessageCallback(msg);
};