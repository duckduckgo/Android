import generateCMPTests from "../playwright/runner";

generateCMPTests('com_wordpressgdpr', [
    'https://www.yourpension.gov.uk/',
], {
    testOptIn: false,
    testSelfTest: false,
});