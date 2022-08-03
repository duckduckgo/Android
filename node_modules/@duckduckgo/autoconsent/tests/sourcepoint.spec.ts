import generateCMPTests from "../playwright/runner";

generateCMPTests('Sourcepoint-frame', [
    'https://www.theguardian.com/',
    'https://www.n-tv.de/',
    'https://www.sueddeutsche.de/',
    'https://news.sky.com/',
]);

generateCMPTests('Sourcepoint-frame', [
    'https://www.insider.com/',
    "https://www.brianmadden.com/",
    "https://www.csoonline.com/blogs",
    "https://www.independent.co.uk/",
], {
    skipRegions: ["US", "GB"],
});
