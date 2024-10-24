module Rouge
  module Formatters
    class HTMLPygments < Formatter
      def initialize(inner, css_class='codehilite')
        @inner = inner
        @css_class = css_class
      end

      def stream(tokens, &b)
        yield %(<pre class="#{@css_class}"><code>)
        @inner.stream(tokens, &b)
        yield "</code></pre>"
      end
    end
  end
end
