module XCPretty
  class HTML < Reporter

    FILEPATH = 'build/reports/tests.html'
    TEMPLATE = File.expand_path('../../../../assets/report.html.erb', __FILE__)
    SCREENSHOT_DIR = 'build/reports'

    def load_dependencies
      unless @@loaded ||= false
        require 'fileutils'
        require 'pathname'
        require 'erb'
        @@loaded = true
      end
    end

    def initialize(options)
      super(options)
      @test_suites = {}
      @collect_screenshots = options[:screenshots]
    end

    def handle(line)
      @parser.parse(line)
    end

    def format_failing_test(suite, test_case, reason, file)
      add_test(suite, name: test_case, failing: true,
                      reason: reason, file: file,
                      snippet: formatted_snippet(file),
                      screenshots: [])
    end

    def format_passing_test(suite, test_case, time)
      add_test(suite, name: test_case, time: time, screenshots: [])
    end

    private

    def formatted_snippet(filepath)
      snippet = Snippet.from_filepath(filepath)
      Syntax.highlight_html(snippet)
    end

    def add_test(suite_name, data)
      @test_count += 1
      @test_suites[suite_name] ||= {tests: []}
      @test_suites[suite_name][:tests] << data
      if data[:failing]
        @test_suites[suite_name][:failing] = true
        @fail_count += 1
      end
    end

    def write_report
      if @collect_screenshots
        load_screenshots
      end
      File.open(@filepath, 'w') do |f|
        # WAT: get rid of these locals. BTW Cucumber fails if you remove them
        test_suites = @test_suites
        fail_count  = @fail_count
        test_count  = @test_count
        erb = ERB.new(File.open(TEMPLATE, 'r').read)
        f.write erb.result(binding)
      end
    end

    def load_screenshots
      Dir.foreach(SCREENSHOT_DIR) do |item|
        next if item == '.' || item == '..' || File.extname(item) != '.png'

        test = find_test(item)
        next if test.nil?

        test[:screenshots] << item
      end
    end

    def find_test(image_name)
      @test_suites.each do |name, info|
        info[:tests].each do |test, index|
          combined_name = name + '_' + test[:name]
          test_name_matches = image_name.start_with?(test[:name])
          combined_name_matches = image_name.start_with?(combined_name)
          return test if test_name_matches || combined_name_matches
        end
      end
      nil
    end
  end
end

