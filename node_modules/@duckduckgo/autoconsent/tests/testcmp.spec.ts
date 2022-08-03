import generateCMPTests from "../playwright/runner";

generateCMPTests('Test page CMP', [
    'https://privacy-test-pages.glitch.me/features/autoconsent/'
]);
