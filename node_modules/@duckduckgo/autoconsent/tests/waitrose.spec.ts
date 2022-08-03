import generateCMPTests from "../playwright/runner";

generateCMPTests('waitrose.com', [
    'https://waitrose.com'], {
        skipRegions: ["US","FR","DE"]
    }
);
