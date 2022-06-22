/*
 * For a detailed explanation regarding each configuration property, visit:
 * https://jestjs.io/docs/en/configuration.html
 */

module.exports = {
    // Automatically clear mock calls and instances between every test
    clearMocks: true,

    // A list of paths to modules that run some code to configure or set up the testing framework before each test
    setupFilesAfterEnv: ['./jest.setup.js'],

    // The test environment that will be used for testing
    testEnvironment: './jest-test-environment.js',

    // Indicates whether each individual test should be reported during the run
    verbose: true,

    // ensure snapshots are in a JSON format
    snapshotFormat: {
        printBasicPrototype: false
    },
    transformIgnorePatterns: [
        '/node_modules/(?!@duckduckgo/content-scope-scripts)'
    ],
    testPathIgnorePatterns: [
        '/node_modules/',
        '<rootDir>/integration-test'
    ]
}
