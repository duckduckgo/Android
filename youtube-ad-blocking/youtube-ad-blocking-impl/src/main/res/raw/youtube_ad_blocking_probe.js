/**
 * YouTube Ad Blocking — Injection Timing Probe
 *
 * This probe script validates that addDocumentStartJavaScript injection
 * fires before YouTube's own JavaScript initialises. It logs:
 *
 * - Injection timing (performance.now())
 * - Whether ytInitialData is already defined (should be false = we beat YT init)
 * - Whether ytcfg is already defined (should be false)
 * - Frame type (main frame vs iframe)
 * - Current URL
 * - Navigation type (initial load vs SPA navigation via pushState)
 *
 * To use: enable the youTubeAdBlocking feature flag, navigate to youtube.com,
 * and check logcat/console for [DDG-YT-ADBLOCK] messages.
 *
 * Expected success output:
 *   [DDG-YT-ADBLOCK] Injected at 0.5 ms | ytInitialData: false | ytcfg: false | frame: main | url: https://www.youtube.com/
 */
(function() {
    'use strict';

    var TAG = '[DDG-YT-ADBLOCK-PROBE]';
    var timing = performance.now();
    var ytDataDefined = typeof window.ytInitialData !== 'undefined';
    var ytcfgDefined = typeof window.ytcfg !== 'undefined';
    var ytPlayerDefined = typeof window.ytInitialPlayerResponse !== 'undefined';
    var isMainFrame = window === window.top;

    console.log(
        TAG,
        'Injected at', timing.toFixed(2), 'ms',
        '| ytInitialData:', ytDataDefined,
        '| ytcfg:', ytcfgDefined,
        '| ytPlayerResponse:', ytPlayerDefined,
        '| frame:', isMainFrame ? 'main' : 'iframe',
        '| url:', location.href
    );

    // Monitor for SPA navigations to verify script persistence
    if (isMainFrame) {
        var originalPushState = history.pushState;
        var originalReplaceState = history.replaceState;

        history.pushState = function() {
            var result = originalPushState.apply(this, arguments);
            console.log(TAG, 'SPA navigation (pushState) to:', location.href);
            return result;
        };

        history.replaceState = function() {
            var result = originalReplaceState.apply(this, arguments);
            console.log(TAG, 'SPA navigation (replaceState) to:', location.href);
            return result;
        };

        window.addEventListener('popstate', function() {
            console.log(TAG, 'SPA navigation (popstate) to:', location.href);
        });
    }
})();
