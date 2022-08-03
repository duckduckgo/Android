import generateCMPTests from "../playwright/runner";

generateCMPTests('netflix.de', [
    'https://netflix.com'], {
        skipRegions: ["US", "FR", "GB"]
    }
);
