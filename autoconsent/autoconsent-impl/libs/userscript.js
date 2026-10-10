import AutoConsent from '@duckduckgo/autoconsent';

(() => {
    const transportName = 'ddgAutoconsentObj';
    if (Object.prototype.hasOwnProperty.call(globalThis, transportName)) {
        // Capture the WebMessageListener object and remove it, so page scripts cannot use it.
        const transport = globalThis[transportName];
        delete globalThis[transportName];
        const stringify = JSON.stringify;
        const parse = JSON.parse;

        const autoconsent = new AutoConsent((message) => {
            transport.postMessage(stringify(message));
        });
        transport.addEventListener('message', (event) => {
            autoconsent.receiveMessageCallback(parse(event.data));
        });
        return;
    }

    const autoconsent = new AutoConsent((message) => {
        AutoconsentAndroid.process(JSON.stringify(message));
    });
    window.autoconsentMessageCallback = (msg) => {
        autoconsent.receiveMessageCallback(msg);
    };
})();
