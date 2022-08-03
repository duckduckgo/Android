import generateCMPTests from "../playwright/runner";

generateCMPTests('usercentrics-1', [
    'https://hornbach.de',
    'https://dm.de'
], {
        skipRegions: ["US", "GB", "FR"]
    }
);
