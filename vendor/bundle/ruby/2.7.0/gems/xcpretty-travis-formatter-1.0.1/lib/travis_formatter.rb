
class TravisFormatter < XCPretty::Simple

  def initialize (use_unicode, colorize)
    super
    @currentGroup = nil
    @errors = []
    @seenGroups = {}
    @travis = ENV['TRAVIS'].to_s == 'true'
    @warnings = []
    at_exit do
      # Ensure the last opened fold is closed.
      close_fold()

      # Print out any warnings.
      if !@warnings.compact.empty?
        open_fold("Warnings")
        STDOUT.puts @warnings.compact.join("\n")
        close_fold()
      end

      # Print out any errors.
      if !@errors.compact.empty?
        open_fold("Errors")
        STDOUT.puts @errors.compact.join("\n")
        exit(1)
      end
    end
  end

  def format_group(group, track = true)
    group = group.downcase.gsub(/[^a-z\d\-_.]+/, '-').gsub(/-$/, '')
    i = 1
    parts = group.split('.')
    if parts.last =~ /^\d+$/
      last = parts.pop()
      i = last ? last.to_i : 1
      group = parts.join('.')
    end

    if track && @currentGroup != "#{group}.#{i}" && @seenGroups.has_key?(group)
      i = @seenGroups[group] + 1
    end
    
    @seenGroups[group] = i
    "#{group}.#{i}"
  end

  def close_fold()
    return if not @travis or @currentGroup == nil
    STDOUT.puts "travis_fold:end:#{@currentGroup}\n"
    @currentGroup = nil
  end

  def open_fold(group, track = true)
    description = group
    group = format_group(group, track)
    return if @currentGroup == group or not @travis
    close_fold() if @currentGroup != nil
    @currentGroup = group
    STDOUT.puts "travis_fold:start:#{group}\033[33;1m#{description}\033[0m\n"
  end

  # Analyze.
  def format_analyze(file_name, file_path);                                 open_fold("Analyze"); super; end
  def format_analyze_target(target, project, configuration);                open_fold("Analyze"); super; end

  # Build.
  def format_build_target(target, project, configuration);                  open_fold("Build"); super; end
  def format_compile(file_name, file_path);                                 open_fold("Build"); super; end

  # Clean.
  def format_clean(project, target, configuration);                         open_fold("Clean"); super; end
  def format_clean_target(target, project, configuration);                  open_fold("Clean"); super; end
  def format_clean_remove;                                                  open_fold("Clean"); super; end

  # Test.
  def format_test_run_started(name);                                        open_fold("Test"); super; end
  def format_test_suite_started(name);                                      open_fold("Test"); super; end
  def format_failing_test(suite, test, reason, file_path);                  @errors.push(super); super; end
  def format_test_summary(message, failures_per_suite);                     @errors.concat(failures_per_suite.values); super; end

  # Errors and warnings.
  def format_compile_error(file_name, file_path, reason, line, cursor);     @errors.push(super); ""; end
  def format_error(message);                                                @errors.push(super); ""; end
  def format_file_missing_error(error, file_path);                          @errors.push(super); ""; end
  def format_ld_warning(message);                                           @warnings.push(super); ""; end
  def format_undefined_symbols(message, symbol, reference);                 @warnings.push(super); ""; end
  def format_duplicate_symbols(message, file_paths);                        @warnings.push(super); ""; end
  def format_warning(message);                                              @warnings.push(super); ""; end
  def format_compile_warning(file_name, file_path, reason, line, cursor);   @warnings.push(super); ""; end

end

TravisFormatter
