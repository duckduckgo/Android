import generateCMPTests from "../playwright/runner";

generateCMPTests('Tealium', [
    'https://www.bahn.de/',
    'https://www.lufthansa.com/de/en/homepage',
]);
