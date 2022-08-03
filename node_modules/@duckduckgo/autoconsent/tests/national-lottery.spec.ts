import generateCMPTests from "../playwright/runner";

generateCMPTests('national-lottery.co.uk', [
    'https://national-lottery.co.uk'], {
        skipRegions: ["US", "FR", "DE"]
    }
);
