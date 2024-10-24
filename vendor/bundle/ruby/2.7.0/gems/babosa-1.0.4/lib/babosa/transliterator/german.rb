# encoding: utf-8
module Babosa
  module Transliterator
    class German < Latin
      APPROXIMATIONS = {
        "ä" => "ae",
        "ö" => "oe",
        "ü" => "ue",
        "Ä" => "Ae",
        "Ö" => "Oe",
        "Ü" => "Ue"
      }
    end
  end
end
