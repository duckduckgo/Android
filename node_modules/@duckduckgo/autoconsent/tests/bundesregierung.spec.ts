import generateCMPTests from "../playwright/runner";

generateCMPTests('bundesregierung.de', [
    'https://bundesregierung.de'], {
        skipRegions: [
            "US", "FR", "GB",
            "DE" // our crawler proxy hits a bot wall, but it still passes locally
        ]
    }
);
