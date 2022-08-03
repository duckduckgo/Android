import generateCMPTests from "../playwright/runner";

generateCMPTests('wetransfer.com', [
    'https://wetransfer.com'], {
        skipRegions: ["US", "FR", "DE"]
    }
);
