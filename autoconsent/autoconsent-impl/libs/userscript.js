import AutoConsent from '@duckduckgo/autoconsent';
import * as rules from '@duckduckgo/autoconsent/rules/rules.json';

const autoconsent = new AutoConsent(
    (message) => {
        console.log('sending marcos', message.type);
        MARCOS.process(JSON.stringify(message));
    },
    null,
    rules,
);
window.autoconsentMessageCallback = (msg) => {
    MARCOS.console('received');
    autoconsent.receiveMessageCallback(msg);
};