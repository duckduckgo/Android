# encoding: utf-8
module Babosa
  module Transliterator
    class Bulgarian < Cyrillic
      APPROXIMATIONS = {
        "Ж" => "J",
        "Й" => "I",
        "Х" => "H",
        "Ц" => "C",
        "Щ" => "Sht",
        "Ъ" => "U",
        "Ь" => "I",
        "Ю" => "Iu",
        "Я" => "Ia",
        "ж" => "j",
        "й" => "i",
        "х" => "h",
        "ц" => "c",
        "щ" => "sht",
        "ъ" => "u",
        "ь" => "i",
        "ю" => "iu",
        "я" => "ia"
      }
    end
  end
end
