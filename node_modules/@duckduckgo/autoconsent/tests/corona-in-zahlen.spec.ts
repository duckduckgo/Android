import generateCMPTests from "../playwright/runner";

generateCMPTests('corona-in-zahlen.de', [
    'http://corona-in-zahlen.de'], {
        skipRegions: ["US", "FR", "GB"]
    }
);
