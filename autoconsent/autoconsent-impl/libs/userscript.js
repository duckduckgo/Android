import AutoConsent from '@duckduckgo/autoconsent';

const autoconsent = new k(
    (message) => {
        console.log('sending', message);
        var msg = MARCOS.process(message);
    },
);
window.autoconsentMessageCallback = (msg) => {
    MARCOS.console('squifoso');
    autoconsent.receiveMessageCallback(msg);
};ss