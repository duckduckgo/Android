import generateCMPTests from "../playwright/runner";

generateCMPTests('hl.co.uk', [
    'https://hl.co.uk'], {
        skipRegions: ["US", "DE", "GB"]
    }
);
