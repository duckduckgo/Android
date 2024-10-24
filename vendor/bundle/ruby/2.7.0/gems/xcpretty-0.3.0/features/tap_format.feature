Feature: Showing Test Anything Protocol-compatible test output

    Scenario: Showing failed tests
        Given I have a failing test in my suite
        When I pipe to xcpretty with "--tap"
        Then I should see text beginning with "not ok 1"

    Scenario: Showing passing tests
        Given I have a passing test in my suite
        When I pipe to xcpretty with "--tap"
        Then I should see text beginning with "ok 1"

    Scenario: Showing pending tests
        Given I have a pending test in my suite
        When I pipe to xcpretty with "--tap"
        Then I should see text containing " # TODO" and beginning with "not ok 1"

    Scenario: Showing how many tests completed
        Given I have a pending test in my suite
        And I have a passing test in my suite
        And I have a failing test in my suite
        And the test suite has finished
        When I pipe to xcpretty with "--tap"
        Then I should see text matching "1..3"

    Scenario: Showing a running test counter
        Given I have a passing test in my suite
        And I have a failing test in my suite
        When I pipe to xcpretty with "--tap"
        Then I should see text beginning with "ok 1"
        And I should see text beginning with "not ok 2"
