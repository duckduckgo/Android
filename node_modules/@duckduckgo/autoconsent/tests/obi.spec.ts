import generateCMPTests from "../playwright/runner";

generateCMPTests('obi.de', [
    'https://obi.de'], {
        skipRegions: ["US", "FR", "GB"]
    }
);
