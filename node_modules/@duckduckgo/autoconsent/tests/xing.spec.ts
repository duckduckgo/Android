import generateCMPTests from "../playwright/runner";

generateCMPTests('xing.com', [
    'https://www.xing.com/start/signup'], {
        skipRegions: ["US", "FR", "DE"],
        testSelfTest: false
    }
);
