import generateCMPTests from "../playwright/runner";

generateCMPTests('snigel', [
    'https://w3schools.com'], {
        skipRegions: ["US"]
    }
);
