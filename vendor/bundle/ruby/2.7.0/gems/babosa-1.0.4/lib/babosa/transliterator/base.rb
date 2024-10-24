# encoding: utf-8

require 'singleton'

module Babosa

  module Transliterator

    autoload :Bulgarian,  "babosa/transliterator/bulgarian"
    autoload :Cyrillic,   "babosa/transliterator/cyrillic"
    autoload :Danish,     "babosa/transliterator/danish"
    autoload :German,     "babosa/transliterator/german"
    autoload :Hindi,      "babosa/transliterator/hindi"
    autoload :Latin,      "babosa/transliterator/latin"
    autoload :Macedonian, "babosa/transliterator/macedonian"
    autoload :Norwegian,  "babosa/transliterator/norwegian"
    autoload :Romanian,   "babosa/transliterator/romanian"
    autoload :Russian,    "babosa/transliterator/russian"
    autoload :Serbian,    "babosa/transliterator/serbian"
    autoload :Spanish,    "babosa/transliterator/spanish"
    autoload :Swedish,    "babosa/transliterator/swedish"
    autoload :Ukrainian,  "babosa/transliterator/ukrainian"
    autoload :Greek,      "babosa/transliterator/greek"
    autoload :Vietnamese, "babosa/transliterator/vietnamese"
    autoload :Turkish,    "babosa/transliterator/turkish"

    def self.get(symbol)
      class_name = symbol.to_s.split("_").map {|a| a.gsub(/\b('?[a-z])/) { $1.upcase }}.join
      const_get(class_name)
    end

    class Base
      include Singleton

      APPROXIMATIONS = {
        "×" => "x",
        "÷" => "/",
        "‐" => "-",
        "‑" => "-",
        "‒" => "-",
        "–" => "-",
        "—" => "-",
        "―" => "-",
        "‘" => "'",
        "‛" => "'",
        "“" => '"',
        "”" => '"',
        "„" => '"',
        "‟" => '"',
        '’' => "'",
        '，' => ",",
        '。' => ".",
        '！' => "!",
        '？' => '?',
        '、' => ',',
        '（' => '(',
        '）' => ')',
        '【' => '[',
        '】' => ']',
        '；' => ';',
        '：' => ':',
        '《' => '<',
        '》' => '>',
        # various kinds of space characters
        "\xc2\xa0"     => " ",
        "\xe2\x80\x80" => " ",
        "\xe2\x80\x81" => " ",
        "\xe2\x80\x82" => " ",
        "\xe2\x80\x83" => " ",
        "\xe2\x80\x84" => " ",
        "\xe2\x80\x85" => " ",
        "\xe2\x80\x86" => " ",
        "\xe2\x80\x87" => " ",
        "\xe2\x80\x88" => " ",
        "\xe2\x80\x89" => " ",
        "\xe2\x80\x8a" => " ",
        "\xe2\x81\x9f" => " ",
        "\xe3\x80\x80" => " ",
      }.freeze

      attr_reader :approximations

      def initialize
        if self.class < Base
          @approximations = self.class.superclass.instance.approximations.dup
        else
          @approximations = {}
        end
        self.class.const_get(:APPROXIMATIONS).inject(@approximations) do |memo, object|
          index       = object[0].unpack("U").shift
          value       = object[1].unpack("C*")
          memo[index] = value.length == 1 ? value[0] : value
          memo
        end
        @approximations.freeze
      end

      # Accepts a single UTF-8 codepoint and returns the ASCII character code
      # used as the transliteration value.
      def [](codepoint)
        @approximations[codepoint]
      end

      # Transliterates a string.
      def transliterate(string)
        string.unpack("U*").map {|char| self[char] || char}.flatten.pack("U*")
      end
    end
  end
end
