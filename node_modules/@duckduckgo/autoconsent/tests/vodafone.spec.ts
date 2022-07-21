import generateCMPTests from "../playwright/runner";

generateCMPTests('vodafone.de', [
    'https://vodafone.de'], {
        skipRegions: ["US", "FR", "GB"]
    }
);
