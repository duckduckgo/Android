import generateCMPTests from "../playwright/runner";

generateCMPTests('com_didomi.io', [
    'https://www.ghacks.net/',
    "https://www.20minutes.fr/",
    "https://www.planet.fr/",
    "http://www.allocine.fr/",
    "https://www.boursorama.com/",
], {
    testOptOut: false,
    testSelfTest: false,
    skipRegions: ["US"],
});
