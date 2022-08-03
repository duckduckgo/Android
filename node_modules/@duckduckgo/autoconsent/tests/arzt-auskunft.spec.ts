import generateCMPTests from "../playwright/runner";

generateCMPTests('arzt-auskunft.de', [
    'https://arzt-auskunft.de'], {
        skipRegions: ["US", "FR", "GB"]
    }
);
