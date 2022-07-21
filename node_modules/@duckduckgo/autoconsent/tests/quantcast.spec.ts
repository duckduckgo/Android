import generateCMPTests from "../playwright/runner";

generateCMPTests('quantcast', [
    'https://www.cyclingnews.com/',
    'https://www.techradar.com/',
    "https://www.anandtech.com/",
    "https://www.livescience.com",
    "https://www.gamesradar.com",
], {
    skipRegions: ["US", "GB", "FR"]
});

generateCMPTests('com_quantcast2', [
    'https://www.fandom.com/',
], {
    testOptOut: false,
    testSelfTest: false,
    skipRegions: ["US"]
});
