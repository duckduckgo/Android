# -*- coding: utf-8 -*- #

module Rouge
  module Lexers
    class ShellSession < RegexLexer
      tag 'shell_session'
      title "Shell Session"
      desc 'A generic lexer for shell session and command line'
      aliases 'terminal', 'console'
      filenames '*.cap'

      state :root do
        rule /^([^ \n]*# )([^ \n]*)(.*(\n|$))/ do |m|
          token Name::Entity, m[1]
          token Name::Class, m[2]
          token Keyword::Variable, m[3]
        end
        rule /^([^ \n]*\$ )([^ \n]*)(.*(\n|$))/ do |m|
          token Text::Whitespace, m[1]
          token Name::Class, m[2]
          token Keyword::Variable, m[3]
        end
        rule /^<...>$/, Comment
        rule /.*\n/, Text
      end
    end
  end
end
### tag function class label
