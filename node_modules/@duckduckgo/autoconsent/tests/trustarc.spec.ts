import generateCMPTests from "../playwright/runner";

generateCMPTests('TrustArc-top', [
    'https://www.samsung.com/uk/smartphones/all-smartphones/',
], {
    testOptOut: true,
    testSelfTest: false,
    skipRegions: ["US"]
});

generateCMPTests('TrustArc-top', [
    'https://www.garmin.com/de-DE/',
], {
    testOptOut: true,
    testSelfTest: false,
    skipRegions: ["US", "FR"]
});

generateCMPTests('TrustArc-frame', [
    'https://www.garmin.com/de-DE/',
], {
    testOptOut: true,
    testSelfTest: false,
    onlyRegions: ["FR"]
});

generateCMPTests('TrustArc-frame', [
    'https://www.wish.com/',
    'https://www.forbes.com/',
    'https://www.starbucks.com/',
], {
    testOptOut: true,
    testSelfTest: false,
    skipRegions: ["US"]
});
