import generateCMPTests from "../playwright/runner";

generateCMPTests('steampowered.com', [
    'https://store.steampowered.com'], {
        skipRegions: ["US"]
    }
);
