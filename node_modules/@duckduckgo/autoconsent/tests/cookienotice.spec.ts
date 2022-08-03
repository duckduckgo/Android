import generateCMPTests from "../playwright/runner";

generateCMPTests('cookie-notice', [
    'https://electricbikereview.com/',
    'https://osxdaily.com/',
]);
