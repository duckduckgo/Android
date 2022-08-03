import generateCMPTests from "../playwright/runner";

generateCMPTests('thefreedictionary.com', [
    'https://thefreedictionary.com'], {
        skipRegions: ["US"]
    }
);
