import generateCMPTests from "../playwright/runner";

generateCMPTests('moneysavingexpert.com', [
    'https://moneysavingexpert.com'], {
        skipRegions: ["US", "FR", "DE"]
    }
);
