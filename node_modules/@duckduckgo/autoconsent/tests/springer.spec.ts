import generateCMPTests from "../playwright/runner";

generateCMPTests('com_springer', [
    'https://www.beobachter.ch/',
    'https://www.blick.ch/',
    'https://www.onet.pl/',
], {
    skipRegions: ['GB'],
    testOptOut: false,
    testSelfTest: false,
});