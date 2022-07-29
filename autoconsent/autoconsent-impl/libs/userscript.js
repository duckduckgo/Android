import AutoConsent from '@duckduckgo/autoconsent';

const autoconsent = new AutoConsent(
    (message) => {
        console.log('sending marcos', message.type);
        MARCOS.process(JSON.stringify(message));
    },
);
window.autoconsentMessageCallback = (msg) => {
    MARCOS.console('received');
    autoconsent.receiveMessageCallback(msg);
};