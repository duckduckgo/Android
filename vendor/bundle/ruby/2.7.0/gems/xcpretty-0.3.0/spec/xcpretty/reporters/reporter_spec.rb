# encoding: utf-8

require 'xcpretty'
require 'fixtures/constants'

module XCPretty

  describe Reporter do

    before(:each) do
      @reporter = Reporter.new(path: "example_file")
    end

    it "reports a passing test" do
      @reporter.format_passing_test("RACCommandSpec", "_tupleByAddingObject__should_add_a_non_nil_object", "0.001")
      @reporter.tests.should include("_tupleByAddingObject__should_add_a_non_nil_object PASSED")
    end

    it "reports a failing test" do
      @reporter.format_failing_test("RACCommandSpec", "enabled_signal_should_send_YES_while_executing_is_YES_and_allowsConcurrentExecution_is_YES", "expected: 1, got: 0", 'path/to/file')
      @reporter.tests.should include("enabled_signal_should_send_YES_while_executing_is_YES_and_allowsConcurrentExecution_is_YES in path/to/file FAILED: expected: 1, got: 0")
    end

    it "reports a pending test" do
      @reporter.format_pending_test("RACCommandSpec", "_tupleByAddingObject__should_add_a_non_nil_object")
      @reporter.tests.should include("_tupleByAddingObject__should_add_a_non_nil_object IS PENDING")
    end

    it "writes to disk" do
      @reporter.format_passing_test("RACCommandSpec", "_tupleByAddingObject__should_add_a_non_nil_object", "0.001")
      file = double("file stub")
      File.should_receive(:open).with("example_file", "w").and_yield(file)
      file.should_receive(:write).with("_tupleByAddingObject__should_add_a_non_nil_object PASSED\nFINISHED RUNNING 1 TESTS WITH 0 FAILURES")
      @reporter.write_report

    end

  end
end

