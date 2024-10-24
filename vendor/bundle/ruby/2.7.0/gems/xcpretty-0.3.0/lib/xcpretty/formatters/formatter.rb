# encoding: utf-8
require 'xcpretty/ansi'
require 'xcpretty/parser'

module XCPretty

  # Making a new formatter is easy.
  # Just make a subclass of Formatter, and override any of these methods.
  module FormatMethods
    EMPTY = ''.freeze

    def format_analyze(file_name, file_path);                  EMPTY; end
    def format_build_target(target, project, configuration);   EMPTY; end
    def format_aggregate_target(target, project, configuration); EMPTY; end
    def format_analyze_target(target, project, configuration); EMPTY; end
    def format_check_dependencies;                             EMPTY; end
    def format_clean(project, target, configuration);          EMPTY; end
    def format_clean_target(target, project, configuration);   EMPTY; end
    def format_clean_remove;                                   EMPTY; end
    def format_compile(file_name, file_path);                  EMPTY; end
    def format_compile_command(compiler_command, file_path);   EMPTY; end
    def format_compile_storyboard(file_name, file_path);       EMPTY; end
    def format_compile_xib(file_name, file_path);              EMPTY; end
    def format_copy_header_file(source, target);               EMPTY; end
    def format_copy_plist_file(source, target);                EMPTY; end
    def format_copy_strings_file(file_name);                   EMPTY; end
    def format_cpresource(file);                               EMPTY; end
    def format_generate_dsym(dsym);                            EMPTY; end
    def format_linking(file, build_variant, arch);             EMPTY; end
    def format_libtool(library);                               EMPTY; end
    def format_passing_test(suite, test, time);                EMPTY; end
    def format_pending_test(suite, test);                      EMPTY; end
    def format_measuring_test(suite, test, time);              EMPTY; end
    def format_failing_test(suite, test, reason, file_path);   EMPTY; end
    def format_process_pch(file);                              EMPTY; end
    def format_process_pch_command(file_path);                 EMPTY; end
    def format_phase_success(phase_name);                      EMPTY; end
    def format_phase_script_execution(script_name);            EMPTY; end
    def format_process_info_plist(file_name, file_path);       EMPTY; end
    def format_codesign(file);                                 EMPTY; end
    def format_preprocess(file);                               EMPTY; end
    def format_pbxcp(file);                                    EMPTY; end
    def format_shell_command(command, arguments);              EMPTY; end
    def format_test_run_started(name);                         EMPTY; end
    def format_test_run_finished(name, time);                  EMPTY; end
    def format_test_suite_started(name);                       EMPTY; end
    def format_test_summary(message, failures_per_suite);      EMPTY; end
    def format_touch(file_path, file_name);                    EMPTY; end
    def format_tiffutil(file);                                 EMPTY; end
    def format_write_file(file);                               EMPTY; end
    def format_write_auxiliary_files;                          EMPTY; end
    def format_other(text)                                     EMPTY; end

    # COMPILER / LINKER ERRORS AND WARNINGS
    def format_compile_error(file_name, file_path, reason,
                             line, cursor);                    EMPTY; end
    def format_error(message);                                 EMPTY; end
    def format_file_missing_error(error, file_path);           EMPTY; end
    def format_ld_warning(message);                            EMPTY; end
    def format_undefined_symbols(message, symbol, reference);  EMPTY; end
    def format_duplicate_symbols(message, file_paths);         EMPTY; end
    def format_warning(message);                             message; end

    # TODO: see how we can unify format_error and format_compile_error,
    #       the same for warnings
    def format_compile_warning(file_name, file_path, reason,
                               line, cursor);                  EMPTY; end
  end

  class Formatter

    include ANSI
    include FormatMethods

    attr_reader :parser

    def initialize(use_unicode, colorize)
      @use_unicode = use_unicode
      @colorize = colorize
      @parser = Parser.new(self)
    end

    def finish
    end

    # Override if you want to catch something specific with your regex
    def pretty_format(text)
      parser.parse(text)
    end

    # If you want to print inline, override #optional_newline with ''
    def optional_newline
      "\n"
    end

    def use_unicode?
      !!@use_unicode
    end

    # Will be printed by default. Override with '' if you don't want summary
    def format_test_summary(executed_message, failures_per_suite)
      failures = format_failures(failures_per_suite)
      if failures.empty?
        final_message = green(executed_message)
      else
        final_message = red(executed_message)
      end

      text = [failures, final_message].join("\n\n\n").strip
      "\n\n#{text}"
    end

    ERROR = '❌ '
    ASCII_ERROR = '[x]'

    WARNING = '⚠️ '
    ASCII_WARNING = '[!]'

    def format_error(message)
      "\n#{red(error_symbol + " " + message)}\n\n"
    end

    def format_compile_error(file, file_path, reason, line, cursor)
      "\n#{red(error_symbol + " ")}#{file_path}: #{red(reason)}\n\n" \
        "#{line}\n#{cyan(cursor)}\n\n"
    end

    def format_file_missing_error(reason, file_path)
      "\n#{red(error_symbol + " " + reason)} #{file_path}\n\n"
    end

    def format_compile_warning(file, file_path, reason, line, cursor)
      "\n#{yellow(warning_symbol + ' ')}#{file_path}: #{yellow(reason)}\n\n" \
        "#{line}\n#{cyan(cursor)}\n\n"
    end

    def format_ld_warning(reason)
      "#{yellow(warning_symbol + ' ' + reason)}"
    end

    def format_undefined_symbols(message, symbol, reference)
      "\n#{red(error_symbol + " " + message)}\n" \
        "> Symbol: #{symbol}\n" \
        "> Referenced from: #{reference}\n\n"
    end

    def format_duplicate_symbols(message, file_paths)
      "\n#{red(error_symbol + " " + message)}\n" \
        "> #{file_paths.map { |path| path.split('/').last }.join("\n> ")}\n"
    end

    def format_will_not_be_code_signed(message)
      "#{yellow(warning_symbol + " " + message)}"
    end

    def format_other(text)
      ""
    end


    private

    def format_failures(failures_per_suite)
      failures_per_suite.map do |suite, failures|
        formatted_failures = failures.map do |failure|
          format_failure(failure)
        end.join("\n\n")

        "\n#{suite}\n#{formatted_failures}"
      end.join("\n")
    end

    def format_failure(f)
      snippet = Snippet.from_filepath(f[:file_path])
      output = "  #{f[:test_case]}, #{red(f[:reason])}"
      output += "\n  #{cyan(f[:file_path])}"
      return output if snippet.contents.empty?

      output += "\n  ```\n"
      if @colorize
        output += Syntax.highlight(snippet)
      else
        output += snippet.contents
      end

      output += "  ```"
      output
    end

    def error_symbol
      use_unicode? ? ERROR : ASCII_ERROR
    end

    def warning_symbol
      use_unicode? ? WARNING : ASCII_WARNING
    end

  end
end

