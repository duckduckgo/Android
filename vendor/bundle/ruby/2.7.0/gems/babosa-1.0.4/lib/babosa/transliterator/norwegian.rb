# encoding: utf-8
module Babosa
  module Transliterator
    class Norwegian < Latin
      APPROXIMATIONS = {
        "ø" => "oe",
        "å" => "aa",
        "Ø" => "Oe",
        "Å" => "Aa"
      }
    end
  end
end

