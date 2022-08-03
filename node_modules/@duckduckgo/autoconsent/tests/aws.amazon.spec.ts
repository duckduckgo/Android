import generateCMPTests from "../playwright/runner";

generateCMPTests('aws.amazon.com', [
    'https://docs.aws.amazon.com'], {
        skipRegions: ["US"]
    }
);
