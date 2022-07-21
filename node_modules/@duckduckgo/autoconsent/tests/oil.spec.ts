import generateCMPTests from "../playwright/runner";

generateCMPTests('com_oil', [
    'https://www.lastminute.com/',
    'https://www.nubert.de/',
], {
    skipRegions: ['GB'],
    testOptOut: false,
    testSelfTest: false,
});