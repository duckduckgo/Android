import AutoConsent from '@duckduckgo/autoconsent';
import * as rules from '@duckduckgo/autoconsent/rules/rules.json';

const autoconsent = new k(
    (message) => {
        console.log('sending', message);
        var msg = MARCOS.process(message);
    },
    null,
    rules,
);
window.autoconsentMessageCallback = (msg) => {
    MARCOS.console('received');
    autoconsent.receiveMessageCallback(msg);
};ss