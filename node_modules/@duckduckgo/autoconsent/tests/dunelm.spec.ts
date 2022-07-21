import generateCMPTests from "../playwright/runner";

generateCMPTests('dunelm.com', [
    'https://dunelm.com'], {
        skipRegions: ["US", "FR", "DE"]
    }
);
