import generateCMPTests from "../playwright/runner";

generateCMPTests('deepl.com', [
    'https://deepl.com'], {
        skipRegions: ["US"]
    }
);
