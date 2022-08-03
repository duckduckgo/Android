import generateCMPTests from "../playwright/runner";

generateCMPTests('motor-talk.de', [
    'https://motor-talk.de'], {
        skipRegions: ["US", "FR", "GB"]
    }
);
