import generateCMPTests from "../playwright/runner";

generateCMPTests('metoffice.gov.uk', [
    'https://metoffice.gov.uk'], {
        skipRegions: ["US", "DE", "GB"]
    }
);
