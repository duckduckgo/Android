module XCPretty
  class Snippet
    attr_reader :contents, :file_path

    def initialize(contents = '', file_path = '')
      @contents = contents
      @file_path = file_path
    end

    def self.from_filepath(filepath)
      path, line = filepath.split(':')
      file = File.open(path)

      text = read_snippet(file, line)

      file.close
      new(text, filepath)
    rescue
      new('', filepath)
    end


    def self.read_snippet(file, around_line)
      text = ''
      starting_position = around_line.to_i - 2
      starting_position.times { file.gets }
      3.times { text += readline(file) }
      text
    end

    def self.readline(file)
      file.gets
      $_ || ''
    end

  end
end

