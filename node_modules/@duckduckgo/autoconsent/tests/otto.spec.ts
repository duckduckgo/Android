import generateCMPTests from "../playwright/runner";

generateCMPTests('otto.de', [
    'https://otto.de'], {
        skipRegions: ["US", "FR", "GB"]
    }
);
