import generateCMPTests from "../playwright/runner";

generateCMPTests('Onetrust', [
    'https://stackoverflow.com/',
    'https://www.zdf.de/',
    "https://www.lovescout24.de/",
    "https://www.okcupid.com/",
    "https://doodle.com/",
]);

generateCMPTests('Onetrust', [
    'https://mailchimp.com/',
    "https://www.accenture.com/",
    'https://www.zoom.us',
], {
    testOptIn: false,
});

// opt-in is not necessary in the US on this sites
generateCMPTests('Onetrust', [
    'https://mailchimp.com/',
    "https://www.accenture.com/",
    'https://www.zoom.us',
], {
    testOptIn: true,
    testOptOut: false,
    skipRegions: ['US'],
});

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
