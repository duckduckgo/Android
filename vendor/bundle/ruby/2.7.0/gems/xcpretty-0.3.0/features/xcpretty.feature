Feature: CLI behavior

    Scenario: Starting xcpretty without any flags
        When I run xcpretty
        Then I should see the help banner

    Scenario: Starting xcpretty with version
        When I run xcpretty with -v
        Then I should see the xcpretty version

    Scenario: Performance
        When I run xcpretty over a big file
        Then the performance should be way faster than running cat

