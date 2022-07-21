import generateCMPTests from "../playwright/runner";

generateCMPTests('Onetrust', [
    'https://mailchimp.com/',
    'https://stackoverflow.com/',
    'https://www.zdf.de/',
    "https://www.accenture.com/",
    "https://www.lovescout24.de/",
    "https://www.okcupid.com/",
    "https://doodle.com/",
    'https://www.zoom.us',
]);

generateCMPTests('Onetrust', [
    'https://arstechnica.com/',
    'https://www.nvidia.com/',
    "https://bitbucket.org/",
    "https://www.atlassian.com/",
], {
    skipRegions: ['US', 'GB']
});

generateCMPTests('Onetrust', [
    "https://www.newyorker.com/",
    "https://www.adobe.com/de/",
    "https://about.gitlab.com",
], {
    skipRegions: ['US']
});
