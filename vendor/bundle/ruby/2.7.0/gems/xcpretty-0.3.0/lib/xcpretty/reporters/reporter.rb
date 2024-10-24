module XCPretty

  class Reporter < Formatter
    FILEPATH = 'build/reports/tests.txt'

    attr_reader :tests

    def load_dependencies
      unless @@loaded ||= false
        require 'fileutils'
        @@loaded = true
      end
    end

    def initialize(options)
      super(true, true)
      load_dependencies
      @filepath = options[:path] || self.class::FILEPATH
      @test_count = 0
      @fail_count = 0
      @tests = []
    end

    def handle(line)
      @parser.parse(line)
    end

    def finish
      FileUtils.mkdir_p(File.dirname(@filepath))
      write_report
    end

    def format_failing_test(suite, test_case, reason, file)
      @test_count += 1
      @fail_count += 1
      @tests.push("#{test_case} in #{file} FAILED: #{reason}")
    end

    def format_passing_test(suite, test_case, time)
      @test_count += 1
      @tests.push("#{test_case} PASSED")
    end

    def format_pending_test(classname, test_case)
      @test_count += 1
      @tests.push("#{test_case} IS PENDING")
    end

    def write_report
      File.open(@filepath, 'w') do |f|
        # WAT: get rid of these locals. BTW Cucumber fails if you remove them
        output_string = @tests.join("\n")
        output_string +=
          "\nFINISHED RUNNING #{@test_count} TESTS WITH #{@fail_count} FAILURES"
        f.write output_string
      end
    end

  end

end

