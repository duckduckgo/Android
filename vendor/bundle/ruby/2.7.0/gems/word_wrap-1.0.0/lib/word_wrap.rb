# Copyright (c) 2014 Radek Pazdera
# Distributed under the MIT License

require "word_wrap/version"
require "word_wrap/wrapper"

module WordWrap
  DEFAULT_WIDTH=80

  def self.ww(text, width=DEFAULT_WIDTH, fit=false)
    w = Wrapper.new(text, width)
    fit ? w.fit : w.wrap
  end
end
