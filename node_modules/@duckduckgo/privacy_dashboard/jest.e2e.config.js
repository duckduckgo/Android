module.exports = {
    preset: 'jest-playwright-preset',
    testMatch: ['**/e2e/**/?(*.)+(e2e).[jt]s?(x)'],
    testEnvironmentOptions: {
        'jest-playwright': {
            browsers: ['webkit']
        }
    }
}
