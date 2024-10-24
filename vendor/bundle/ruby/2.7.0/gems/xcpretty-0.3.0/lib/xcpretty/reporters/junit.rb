module XCPretty
  class JUnit < Reporter

    FILEPATH = 'build/reports/junit.xml'

    def load_dependencies
      unless @@loaded ||= false
        require 'fileutils'
        require 'pathname'
        require 'rexml/document'
        require 'rexml/formatters/pretty'
        @@loaded = true
      end
    end

    def initialize(options)
      super(options)
      @directory = `pwd`.strip
      @document  = REXML::Document.new
      @document << REXML::XMLDecl.new('1.0', 'UTF-8')
      @document.add_element('testsuites')
      @total_fails = 0
      @total_tests = 0
    end

    def handle(line)
      @parser.parse(line)
    end

    def format_test_run_started(name)
      @document.root.add_attribute('name', name)
    end

    def format_passing_test(classname, test_case, time)
      test_node = suite(classname).add_element('testcase')
      test_node.attributes['classname'] = classname
      test_node.attributes['name']      = test_case
      test_node.attributes['time']      = time
      @test_count += 1
    end

    def format_pending_test(classname, test_case)
      test_node = suite(classname).add_element('testcase')
      test_node.attributes['classname'] = classname
      test_node.attributes['name']      = test_case
      test_node.add_element('skipped')
      @test_count += 1
    end

    def format_failing_test(classname, test_case, reason, file)
      test_node = suite(classname).add_element('testcase')
      test_node.attributes['classname'] = classname
      test_node.attributes['name']      = test_case
      fail_node = test_node.add_element('failure')
      fail_node.attributes['message'] = reason
      fail_node.text = file.sub(@directory + '/', '')
      @test_count += 1
      @fail_count += 1
    end

    def finish
      set_test_counters
      @document.root.attributes['tests'] = @total_tests
      @document.root.attributes['failures'] = @total_fails
      super
    end

    def write_report
      formatter = REXML::Formatters::Pretty.new(2)
      formatter.compact = true
      output_file = File.open(@filepath, 'w+')
      result = formatter.write(@document, output_file)
      output_file.close
      result
    end

    private

    def suite(classname)
      if @last_suite && @last_suite.attributes['name'] == classname
        return @last_suite
      end

      set_test_counters
      @last_suite = @document.root.add_element('testsuite')
      @last_suite.attributes['name'] = classname
      @last_suite
    end

    def set_test_counters
      if @last_suite
        @last_suite.attributes['tests'] = @test_count
        @last_suite.attributes['failures'] = @fail_count
      end
      @total_fails += @fail_count || 0
      @total_tests += @test_count || 0
      @test_count = 0
      @fail_count = 0
    end
  end
end

