# -*- coding: utf-8 -*- #

module Rouge
  module Lexers
    load_lexer 'javascript.rb'

    class Typescript < Javascript
      title "TypeScript"
      desc "TypeScript, a superset of JavaScript"

      tag 'typescript'
      aliases 'ts'

      filenames '*.ts', '*.d.ts'

      mimetypes 'text/typescript'

      def self.keywords
        @keywords ||= super + Set.new(%w(
          import export from as is
          namespace new static private protected public
          super async await extends implements readonly
        ))
      end

      def self.declarations
        @declarations ||= super + Set.new(%w(
          const type constructor abstract
        ))
      end

      def self.reserved
        @reserved ||= super + Set.new(%w(
          string any number namespace module
          declare default interface
        ))
      end

      def self.builtins
        @builtins ||= super + %w(
          Promise Set Map WeakSet WeakMap Symbol
        )
      end
    end
  end
end
