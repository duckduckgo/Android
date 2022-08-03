import generateCMPTests from "../playwright/runner";

generateCMPTests('eu-cookie-compliance-banner', [
    'https://www.bauwion.de/',
    'https://publichealth.jhu.edu/',
]);
