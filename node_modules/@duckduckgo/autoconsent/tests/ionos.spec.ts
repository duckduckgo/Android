import generateCMPTests from "../playwright/runner";

generateCMPTests('ionos.de', [
    'https://ionos.de'], {
        skipRegions: ["US", "FR", "GB"]
    }
);
