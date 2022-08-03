import generateCMPTests from "../playwright/runner";

generateCMPTests('cc_banner', [
    'https://www.w3resource.com/',
    'https://bitcoin.org/en/',
])
generateCMPTests('cc_banner', [
    'https://distrowatch.com/',
], {
    skipRegions: ['US', 'GB'],
});
