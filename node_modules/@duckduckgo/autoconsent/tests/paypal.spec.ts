import generateCMPTests from "../playwright/runner";

generateCMPTests('paypal.com', [
    'https://paypal.de',
    'https://paypal.com'
],{
    skipRegions: ["US"],
    testSelfTest: false
});
