# -*- coding: utf-8 -*- #

module Rouge
  module Formatters
    class HTMLInline < HTML
      tag 'html_inline'

      def initialize(theme)
        @theme = theme
      end

      def safe_span(tok, safe_val)
        return safe_val if tok == Token::Tokens::Text

        rules = @theme.style_for(tok).rendered_rules

        "<span style=\"#{rules.to_a.join(';')}\">#{safe_val}</span>"
      end
    end
  end
end

