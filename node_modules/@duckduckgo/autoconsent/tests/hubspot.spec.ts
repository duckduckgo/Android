import generateCMPTests from "../playwright/runner";

generateCMPTests('hubspot', [
    'https://blog.hubspot.com/',
    'https://www.hubspot.de/',
]);