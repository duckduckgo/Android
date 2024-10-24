module XCPretty
  class JSONCompilationDatabase < Reporter

    FILEPATH = 'build/reports/compilation_db.json'

    def load_dependencies
      unless @@loaded ||= false
        require 'fileutils'
        require 'pathname'
        require 'json'
        @@loaded = true
      end
    end

    def initialize(options)
      super(options)
      @compilation_units = []
      @pch_path = nil
      @current_file = nil
      @current_path = nil
    end

    def format_process_pch_command(file_path)
      @pch_path = file_path
    end

    def format_compile(file_name, file_path)
      @current_file = file_name
      @current_path = file_path
    end

    def format_compile_command(compiler_command, file_path)
      directory = file_path.gsub("#{@current_path}", '').gsub(/\/$/, '')
      directory = '/' if directory.empty?

      cmd = compiler_command
      cmd = cmd.gsub(/(\-include)\s.*\.pch/, "\\1 #{@pch_path}") if @pch_path

      @compilation_units << {command: cmd,
                             file: @current_path,
                             directory: directory}
    end

    def write_report
      File.open(@filepath, 'w') do |f|
        f.write(@compilation_units.to_json)
      end
    end
  end
end

