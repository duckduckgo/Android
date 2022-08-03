import generateCMPTests from "../playwright/runner";

generateCMPTests('uswitch.com', [
    'https://uswitch.com'], {
        skipRegions: ["US", "FR", "DE"]
    }
);
