import generateCMPTests from "../playwright/runner";

generateCMPTests('ausopen.com', [
    'https://www.ausopen.com/',
], {
    skipRegions: ["US", "FR", "DE"]
});
