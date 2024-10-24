module XCPretty

  class TestAnything < Knock

    attr_reader :counter

    def initialize(unicode, color)
      super
      @counter = 0
    end

    def format_passing_test(suite, test_case, time)
      increment_counter
      "#{PASS} #{counter} - #{test_case}"
    end

    def format_failing_test(test_suite, test_case, reason, file)
      increment_counter
      "#{FAIL} #{counter} - #{test_case}" +
      format_failure_diagnostics(test_suite, test_case, reason, file)
    end

    def format_pending_test(test_suite, test_case)
      increment_counter
      "#{FAIL} #{counter} - #{test_case} # TODO Not written yet"
    end

    def format_test_summary(executed_message, failures_per_suite)
      counter > 0 ? "1..#{counter}" : ''
    end

    private

    def increment_counter
      @counter += 1
    end
  end

end

