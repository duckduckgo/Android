
module XCPretty
  module Term

    def self.unicode?
      Encoding.default_external == Encoding::UTF_8
    end

    def self.color?
      STDOUT.tty?
    end
  end
end

