#
# core_ext.rb
#
# Copyright (c) 2015  Radek Pazdera
# Distributed under the MIT License
#
# Optional core exensions, adding the fit and wrap methods to the String class
#

class String
  def wrap(width=WordWrap::DEFAULT_WIDTH)
    WordWrap.ww(self, width, false)
  end

  def wrap!(width=WordWrap::DEFAULT_WIDTH)
    replace wrap(width)
  end

  def fit(width=WordWrap::DEFAULT_WIDTH)
    WordWrap.ww(self, width, true)
  end

  def fit!(width=WordWrap::DEFAULT_WIDTH)
    replace fit(width)
  end
end
