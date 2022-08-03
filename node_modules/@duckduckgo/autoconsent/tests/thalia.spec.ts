import generateCMPTests from "../playwright/runner";

generateCMPTests('thalia.de', [
    'https://thalia.de'], {
        skipRegions: ["US", "FR", "GB"]
    }
);
