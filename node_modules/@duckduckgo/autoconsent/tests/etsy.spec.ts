import generateCMPTests from "../playwright/runner";

generateCMPTests('etsy', [
    'https://www.etsy.com/',
], {
    skipRegions: ["US"],
});
