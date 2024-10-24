Feature: Creating a JUnit test report

    Background:
        Given the tests have started running

    Scenario: Showing a test suite
        Given I have a passing test in my suite
        When I pipe to xcpretty with "--report junit"
        Then I should see a test suite node

    Scenario: Showing pending test output
        Given I have a pending test in my suite
        When I pipe to xcpretty with "--report junit"
        Then I should see a pending test node in my report

    Scenario: Showing failed tests
        Given I have a failing test in my suite
        When I pipe to xcpretty with "--report junit"
        Then I should see a failed test node in my report

    Scenario: Showing passing tests
        Given I have a passing test in my suite
        When I pipe to xcpretty with "--report junit"
        Then I should see a passing test node in my report

    Scenario: Counting tests
        Given I have a passing test in my suite
        And I have a failing test in my suite
        And the test suite has finished
        When I pipe to xcpretty with "--report junit"
        Then I should see 2 tests in my report

    Scenario: Having many test classes
        Given I have tests in my suite from 2 classes
        When I pipe to xcpretty with "--report junit"
        Then I should see 2 test suites

    Scenario: Writing to a custom file path
        When I pipe to xcpretty with "--report junit" and specify a custom path
        Then I should have a test report in a custom path

    Scenario: Writing to multiple custom file paths
        When I pipe to xcpretty with two custom "junit" report paths
        Then I should have test reports in two custom paths

    Scenario: Showing tests with one having a swift fatal error
        Given I have a swift fatal error in a test in my suite
        When I pipe to xcpretty with "--report junit"
        Then I should see a failed test node in my report
