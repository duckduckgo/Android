import generateCMPTests from "../playwright/runner";

generateCMPTests('baden-wuerttemberg.de', [
    'https://baden-wuerttemberg.de'], {
        skipRegions: ["US"]
    }
);
