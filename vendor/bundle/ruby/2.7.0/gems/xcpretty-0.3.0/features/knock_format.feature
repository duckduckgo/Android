Feature: Showing Knock-style test output

    Scenario: Showing failed tests
        Given I have a failing test in my suite
        When I pipe to xcpretty with "--knock"
        Then I should see text beginning with "not ok"

    Scenario: Showing passing tests
        Given I have a passing test in my suite
        When I pipe to xcpretty with "--knock"
        Then I should see text beginning with "ok"
