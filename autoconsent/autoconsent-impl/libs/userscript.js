import AutoConsent from '@duckduckgo/autoconsent';

const autoconsent = new AutoConsent(
    (message) => {
        AutoconsentAndroid.process(JSON.stringify(message));
    },
);
window.autoconsentMessageCallback = (msg) => {
    autoconsent.receiveMessageCallback(msg);
};