Feature: Creating a HTML test report

    Background:
        Given the tests have started running

    Scenario: Showing a test suite
        Given I have a passing test in my suite
        When I pipe to xcpretty with "--report html"
        Then I should see a test suite section in HTML

    Scenario: Showing failed tests
        Given I have a failing test in my suite
        When I pipe to xcpretty with "--report html"
        Then I should see a failed test in HTML
        And the failure counter should show 1 test

    Scenario: Showing passing tests
        Given I have a passing test in my suite
        When I pipe to xcpretty with "--report html"
        Then I should see a passing test in HTML

    Scenario: Counting tests
        Given I have a passing test in my suite
        And I have a failing test in my suite
        And the test suite has finished
        When I pipe to xcpretty with "--report html"
        Then I should see 2 tests in HTML

    Scenario: Having many test classes
        Given I have tests in my suite from 2 classes
        When I pipe to xcpretty with "--report html"
        Then I should see 2 test suite sections in HTML

    Scenario: Writing to a custom file path
        When I pipe to xcpretty with "--report html" and specify a custom path
        Then I should have a test report in a custom path

    Scenario: Writing to multiple custom file paths
        When I pipe to xcpretty with two custom "html" report paths
        Then I should have test reports in two custom paths

    Scenario: Showing screenshots
        Given I have a passing test in my suite
        And the test suite has finished
        And I have a screenshot in the output folder
        When I pipe to xcpretty with "--report html --screenshots"
        Then I should see a screenshot in HTML

  Scenario: Preventing unrelated images to be included in final report
        Given I have a passing test in my suite
        And the test suite has finished
        And I have an unrelated image in the output folder
        When I pipe to xcpretty with "--report html --screenshots"
        Then I should not see a screenshot in HTML