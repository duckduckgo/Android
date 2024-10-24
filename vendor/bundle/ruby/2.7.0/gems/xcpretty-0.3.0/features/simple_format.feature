Feature: Showing build output in simple format

    Scenario: Showing file compilation
        Given I have a file to compile
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see a successful compilation message

    Scenario: Showing file compilation with CCache
        Given I have a file to compile with ccache
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see a successful compilation message

    Scenario: Showing xib compilation
        Given I have a xib to compile
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see a successful compilation message

    Scenario: Showing storyboard compilation
        Given I have a storyboard to compile
        When I pipe to xcpretty with "--simple"
        Then I should see a successful compilation message

    Scenario: Showing precompilation
        Given I have a precompiled header
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see a successful precompilation message

    Scenario: Showing phase success
        Given I have completed a build
        When I pipe to xcpretty with "--simple"
        Then I should see a "build" completion message

    Scenario: Showing file compilation with color
        Given I have a file to compile
        When I pipe to xcpretty with "--simple --color"
        Then I should see a yellow completion icon

    Scenario: Showing xib compilation with color
        Given I have a xib to compile
        When I pipe to xcpretty with "--simple --color"
        Then I should see a yellow completion icon

    Scenario: Showing storyboard compilation with color
        Given I have a storyboard to compile
        When I pipe to xcpretty with "--simple --color"
        Then I should see a yellow completion icon

    Scenario: Showing precompilation
        Given I have a precompiled header
        When I pipe to xcpretty with "--simple --color"
        Then I should see a yellow completion icon

    Scenario: Showing aggregate target
        Given I have an aggregate target to build
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see an aggregate target message

    Scenario: Showing analyze
        Given I have a file to analyze
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see a successful analyze message

    Scenario: Showing shallow analyze
        Given I have a file to shallow analyze
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see a successful analyze message

    Scenario: Showing tiff file validation
        Given I have a tiff file to validate
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see a successful tiff validation message

    Scenario: Showing touch file
        Given I have a file to touch
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see a successful touch message

    Scenario: Showing analyze with color
        Given I have a file to analyze
        When I pipe to xcpretty with "--simple --color"
        Then I should see a yellow completion icon

    Scenario: Showing shallow analyze with color
        Given I have a file to shallow analyze
        When I pipe to xcpretty with "--simple --color"
        Then I should see a yellow completion icon

    Scenario: Showing tiff file validation with color
        Given I have a tiff file to validate
        When I pipe to xcpretty with "--simple --color"
        Then I should see a yellow completion icon

    Scenario: Showing touch file with color
        Given I have a file to touch
        When I pipe to xcpretty with "--simple --color"
        Then I should see a yellow completion icon

    Scenario: Showing the start of a test run
        Given the tests have started running
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see that test suite has started

    Scenario: Showing the start of a test suite
        Given I start a test suite
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see the name of suite only

    Scenario: Showing the end of a test suite
        Given the test suite has finished
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see that the test suite finished

    Scenario: Showing failed test output
        Given I have a failing test in my suite
        And the test suite has finished
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see the name of a failed test
        And I should see the path of a failed test

    Scenario: Showing successful test output
        Given I have a passing test in my suite
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see the name of a passing test
        And I should not see the name of the test group
        And I should not see the path of a passing test

    Scenario: Colorizing slow-ish tests in yellow
        Given I have a slow-ish test in my suite
        When I pipe to xcpretty with "--simple --color"
        Then I should see the test time in yellow

    Scenario: Colorizing slow tests in red
        Given I have a slow test in my suite
        When I pipe to xcpretty with "--simple --color"
        Then I should see the test time in red

    Scenario: Showing pending test output
        Given I have a pending test in my suite
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see the name of a pending test

    Scenario: Showing measuring test output
        Given I have a measuring test in my suite
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see the name of a measuring test

    Scenario: Showing failed test output with color
        Given I have a failing test in my suite
        And the test suite has finished
        When I pipe to xcpretty with "--simple --color"
        Then I should see a red failed test mark
        And the final execution message should be red

    Scenario: Showing successful test output with color
        Given I have a passing test in my suite
        And the test suite has finished
        When I pipe to xcpretty with "--simple --color"
        Then I should see a green passing test mark

    Scenario: Running tests without UTF-8 support
        Given I have a passing test in my suite
        And I pipe to xcpretty with "--no-utf --color"
        Then I should see a non-utf prefixed output

    Scenario: Showing code signing
        Given I have a file to code sign
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see a successful code signing message

    Scenario: Showing code signing a framework
        Given I have a framework to code sign
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see a successful code signing message

    Scenario: Showing target will not be code signed warning
        Given I have a target which will not be code signed
        When I pipe to xcpretty with "--simple --color"
        Then I should see a target will not be code signed warning

    Scenario: Showing preprocess
        Given I have a file to preprocess
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see a successful preprocessing message

    Scenario: Showing a PBXCp copy
        Given I have a file to copy with PBXCp
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see a successful copying message

    Scenario: Build fails when Pod install hasn't been run
        Given podfile.lock wasn't in sync
        When I pipe to xcpretty with "--simple --color"
        Then I should see a red error message
        And I should see that sandbox is not in sync with Podfile.lock

    Scenario: Compilation fails because missing files in the project
        Given there was a missing file
        When I pipe to xcpretty with "--simple --color"
        Then I should see a red error message
        And I should see which file is missing

    Scenario: Compilation fails because of syntax errors
        Given there was a syntax error
        When I pipe to xcpretty with "--simple --color"
        Then I should see a red compilation error
        And I should see a failed line
        And I should see a cyan cursor

    Scenario: Linker fails with undefined symbols
        Given the linker has failed with undefined symbols
        When I pipe to xcpretty with "--simple --color"
        Then I should see the undefined symbold message
        And I should see the symbol and reference that caused failure

    Scenario: There are build warnings
        Given there were warnings in the code
        When I pipe to xcpretty with "--simple --color"
        Then I should see a yellow warning message

    Scenario: Showing provisioning profile doesn't support capability
        Given the provisioning profile doesn't support capability
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see the profile doesn't support capability message

    Scenario: Showing provisioning profile doesn't include entitlement
        Given the provisioning profile doesn't include entitlement
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see the profile doesn't include entitlement message

    Scenario: Showing code signing is required error
        Given the target requires code signing
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see the code signing is requried message

    Scenario: Showing no profile matching error
        Given the matching profile is missing
        When I pipe to xcpretty with "--simple --no-color"
        Then I should see the no profile matching message
