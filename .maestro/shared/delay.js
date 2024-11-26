/**
* This is a forced delay, and will unconditionally delay the test and extend the test duration.
* This should be used sparingly. Where possible, prefer `extendedWaitUntil` over this.
* This is only for times when a delay is needed for an event to happen, and that event has no visual change you can wait for.
*
* Usage:   `- runScript: ../shared/delay.js`
*/

function blockFor(ms) {
    const end = Date.now() + ms; // Calculate the end time
    while (Date.now() < end) {
        // Busy-wait loop
    }
}

blockFor(5000)