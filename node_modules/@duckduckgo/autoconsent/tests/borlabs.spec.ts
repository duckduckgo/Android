import generateCMPTests from "../playwright/runner";

generateCMPTests('borlabs', [
    'https://reitschuster.de'], {
        skipRegions: ["US"]
    }
);
