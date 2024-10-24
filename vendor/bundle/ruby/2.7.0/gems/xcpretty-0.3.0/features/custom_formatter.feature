Feature: Loading an arbitrary Ruby file as a formatter

    Scenario: The file loaded does not contain a Ruby class
        When I pipe to xcpretty with "--formatter /bin/bash"
        Then the exit status code should be 1

    Scenario: The file loaded contains a Ruby class
        Given I have a file to compile
        When I pipe to xcpretty with a custom formatter
        Then the exit status code should be 0

    Scenario: Showing output using a custom formatter
        Given I have a file to compile
        When I pipe to xcpretty with a custom formatter
        Then I should see a custom compilation message