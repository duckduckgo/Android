Feature: Loading an arbitrary Ruby file as a reporter

    Background:
        Given the tests have started running

    Scenario: The file loaded does not contain a Ruby class
        When I pipe to xcpretty with "-r /bin/bash"
        Then the exit status code should be 1

    Scenario: The file loaded contains a Ruby class
        Given I have a file to compile
        When I pipe to xcpretty with a custom reporter
        Then the exit status code should be 0

    Scenario: Showing failed tests
        Given I have a failing test in my suite
        When I pipe to xcpretty with a custom reporter
        Then I should see a failed test in my custom report
        And the custom failure counter should show 1 test

    Scenario: Showing passing tests
        Given I have a passing test in my suite
        When I pipe to xcpretty with a custom reporter
        Then I should see a passing test in my custom report

    Scenario: Showing pending tests
        Given I have a pending test in my suite
        When I pipe to xcpretty with a custom reporter
        Then I should see a pending test in my custom report