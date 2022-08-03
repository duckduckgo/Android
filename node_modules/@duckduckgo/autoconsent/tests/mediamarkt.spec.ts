import generateCMPTests from "../playwright/runner";

generateCMPTests('mediamarkt.de', [
    'https://mediamarkt.de'], {
        skipRegions: ["US", "FR", "GB"]
    }
);
