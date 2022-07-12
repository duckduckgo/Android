import AutoConsent from '@duckduckgo/autoconsent';

const autoconsent = new k(
    (message) => {
        var msg = MARCOS.console(message.type);
        console.log('sending', message);
    },
);
window.autoconsentMessageCallback = (msg) => {
    MARCOS.console('squifoso');
    autoconsent.receiveMessageCallback(msg);
};ss