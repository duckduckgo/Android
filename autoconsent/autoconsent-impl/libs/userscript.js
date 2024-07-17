import AutoConsent from '@duckduckgo/autoconsent';
import { collectMetrics } from '@duckduckgo/autoconsent';
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

collectMetrics().then((results) => {
    // pass the results to the native code. ddgPerfMetrics is a custom JS interface
    const resultsJson = JSON.stringify(results);
    ddgPerfMetrics.onMetrics(location.href + ' ' + resultsJson);
    window.alert(`PERF METRICS: ` + resultsJson);
});
