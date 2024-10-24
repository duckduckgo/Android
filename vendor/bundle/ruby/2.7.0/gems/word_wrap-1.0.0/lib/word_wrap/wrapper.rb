#
# wrapper.rb
#
# Copyright (c) 2014, 2015  Radek Pazdera
# Distributed under the MIT License
#

module WordWrap
  # TODO: Refactor similar passages out of the two functions into a common one
  class Wrapper
    def initialize(text, width)
      @text = text
      @width = width
    end

    def fit
      lines = []
      next_line = ""
      @text.lines do |line|
        line.chomp! "\n"
        if line.length == 0
          if next_line.length > 0
            lines.push next_line
            next_line = ""
          end
          lines.push ""
        end

        words = line.split " "

        words.each do |word|
          word.chomp! "\n"

          if next_line.length + word.length < @width
            if next_line.length > 0
              next_line << " " << word
            else
              next_line = word
            end
          else
            if word.length >= @width
              lines.push next_line unless next_line == ""
              lines.push word
              next_line = ""
            else
              lines.push next_line
              next_line = word
            end
          end
        end
      end

      lines.push next_line
      if next_line.length <= 0
        lines.join("\n")
      else
        lines.join("\n") + "\n"
      end
    end

    def wrap
      output = []

      @text.lines do |line|
        line.chomp! "\n"
        if line.length > @width
          new_lines = split_line(line, @width)
          while new_lines.length > 1 && new_lines[1].length > @width
            output.push new_lines[0]
            new_lines = split_line new_lines[1], @width
          end
          output += new_lines
        else
          output.push line
        end
      end
      output.map { |s| s.rstrip! }
      output.join("\n") + "\n"
    end

    def split_line(line, width)
      at = line.index /\s/
      last_at = at

      while at != nil && at < width
        last_at = at
        at = line.index /\s/, last_at + 1
      end

      if last_at == nil
        [line]
      else
        [line[0,last_at], line[last_at+1, line.length]]
      end
    end
  end
end
