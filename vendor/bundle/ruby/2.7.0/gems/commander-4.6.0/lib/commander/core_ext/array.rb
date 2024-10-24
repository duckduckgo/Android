# frozen_string_literal: true

class Array
  ##
  # Split _string_ into an array. Used in
  # conjunction with HighLine's #ask, or #ask_for_array
  # methods, which must respond to #parse.
  #
  # This method allows escaping of whitespace. For example
  # the arguments foo bar\ baz will become ['foo', 'bar baz']
  #
  # === Example
  #
  #   # ask invokes Array#parse
  #   list = ask 'Favorite cookies:', Array
  #
  #   # or use ask_for_CLASS
  #   list = ask_for_array 'Favorite cookies: '
  #

  def self.parse(string)
    # Using reverse + lookahead to work around Ruby 1.8's lack of lookbehind
    # TODO: simplify now that we don't support Ruby 1.8
    string.reverse.split(/\s(?!\\)/).reverse.map { |s| s.reverse.gsub('\\ ', ' ') }
  end
end
