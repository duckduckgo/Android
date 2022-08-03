import generateCMPTests from "../playwright/runner";

generateCMPTests('Drupal', [
    "https://www.drupal.org/"
], {
        skipRegions: ["US"]
    }
);
