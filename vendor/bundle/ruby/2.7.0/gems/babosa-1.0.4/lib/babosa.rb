module Babosa
  def self.jruby15?
    JRUBY_VERSION >= "1.5" rescue false
  end
end

class String
  def to_identifier
    Babosa::Identifier.new self
  end
  alias to_slug to_identifier
end

require "babosa/transliterator/base"
require "babosa/utf8/proxy"
require "babosa/identifier"
