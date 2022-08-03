import generateCMPTests from "../playwright/runner";

generateCMPTests('marksandspencer.com', [
    'https://www.marksandspencer.com/',
], {
    skipRegions: ["US", "FR", "DE"]
});
