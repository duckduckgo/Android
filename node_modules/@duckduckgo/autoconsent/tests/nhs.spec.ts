import generateCMPTests from "../playwright/runner";

generateCMPTests('nhs.uk', [
    'https://nhs.uk'], {
        skipRegions: ["US", "DE", "GB"]
    }
);
