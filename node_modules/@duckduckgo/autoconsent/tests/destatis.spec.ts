import generateCMPTests from "../playwright/runner";

generateCMPTests('destatis.de', [
    'https://destatis.de'], {
        skipRegions: ["US", "FR", "GB"]
    }
);
