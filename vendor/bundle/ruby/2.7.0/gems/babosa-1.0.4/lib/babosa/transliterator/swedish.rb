# encoding: utf-8
module Babosa
  module Transliterator
    class Swedish < Latin
      APPROXIMATIONS = {
        "å" => "aa",
        "ä" => "ae",
        "ö" => "oe",
        "Å" => "Aa",
        "Ä" => "Ae",
        "Ö" => "Oe"
      }
    end
  end
end

