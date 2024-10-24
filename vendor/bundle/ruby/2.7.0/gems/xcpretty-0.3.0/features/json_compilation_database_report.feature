Feature: Create a JSON compilation database

	Scenario: Showing file compilation
        Given I have a file to compile
        When I pipe to xcpretty with "--report json-compilation-database" and specify a custom path
        Then the JSON compilation database should contain an entry with a command
        Then the JSON compilation database should contain an entry with a directory
        Then the JSON compilation database should contain an entry with a file

    Scenario: Showing file compilation with CCache
        Given I have a file to compile with ccache
        When I pipe to xcpretty with "--report json-compilation-database" and specify a custom path
        Then the JSON compilation database should contain an entry with a command
        Then the JSON compilation database should contain an entry with a directory
        Then the JSON compilation database should contain an entry with a file

    Scenario: Handling a complete xcodebuild session
    	Given some big input
    	When I pipe to xcpretty with "--report json-compilation-database" and specify a custom path
    	Then the JSON compilation database should be complete

    Scenario: Writing to a custom file path
        When I pipe to xcpretty with "--report json-compilation-database" and specify a custom path
        Then I should have a JSON compilation database in a custom path

    Scenario: Writing to multiple custom file paths
        When I pipe to xcpretty with two custom "json-compilation-database" report paths
        Then I should have JSON compilation databases in two custom paths

    Scenario: A project with dependencies with no .pch file
        Given some big input
        When I pipe to xcpretty with "--report json-compilation-database" and specify a custom path
        Then entries with a command shouldn't have malformed "-include" directives
