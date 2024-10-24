# encoding: utf-8
require File.expand_path("../spec_helper", __FILE__)

describe Babosa::Identifier do

  it "should respond_to :empty?" do
    expect("".to_slug).to respond_to(:empty?)
  end

  %w[approximate_ascii clean downcase word_chars normalize to_ascii upcase with_dashes].each do |method|
    describe "##{method}" do
      it "should work with invalid UTF-8 strings" do
        expect {"\x93abc".to_slug.send method}.not_to raise_exception
      end
    end
  end

  describe "#word_chars" do
    it "word_chars! should leave only letters and spaces" do
      string = "a*$%^$@!@b$%^&*()*!c"
      expect(string.to_slug.word_chars!).to match(/[a-z ]*/i)
    end
  end

  describe "#transliterate" do
    it "should transliterate to ascii" do
      (0xC0..0x17E).to_a.each do |codepoint|
        ss = [codepoint].pack("U*").to_slug
        expect(ss.approximate_ascii!).to match(/[\x0-\x7f]/)
      end
    end

    it "should transliterate uncomposed utf8" do
      string = [117, 776].pack("U*") # "ü" as ASCII "u" plus COMBINING DIAERESIS
      expect(string.to_slug.approximate_ascii).to eql("u")
    end

    it "should transliterate using multiple transliterators" do
      string = "свободное režģis"
      expect(string.to_slug.approximate_ascii(:latin, :russian)).to eql("svobodnoe rezgis")
    end
  end

  describe "#downcase" do
    it "should lowercase strings" do
      expect("FELIZ AÑO".to_slug.downcase).to eql("feliz año")
    end
  end

  describe "#upcase" do
    it "should uppercase strings" do
      expect("feliz año".to_slug.upcase).to eql("FELIZ AÑO")
    end
  end

  describe "#normalize" do

    it "should allow passing locale as key for :transliterate" do
      expect("ö".to_slug.clean.normalize(:transliterate => :german)).to eql("oe")
    end

    it "should replace whitespace with dashes" do
      expect("a b".to_slug.clean.normalize).to eql("a-b")
    end

    it "should replace multiple spaces with 1 dash" do
      expect("a    b".to_slug.clean.normalize).to eql("a-b")
    end

    it "should replace multiple dashes with 1 dash" do
      expect("male - female".to_slug.normalize).to eql("male-female")
    end

    it "should strip trailing space" do
      expect("ab ".to_slug.normalize).to eql("ab")
    end

    it "should strip leading space" do
      expect(" ab".to_slug.normalize).to eql("ab")
    end

    it "should strip trailing slashes" do
      expect("ab-".to_slug.normalize).to eql("ab")
    end

    it "should strip leading slashes" do
      expect("-ab".to_slug.normalize).to eql("ab")
    end

    it "should not modify valid name strings" do
      expect("a-b-c-d".to_slug.normalize).to eql("a-b-c-d")
    end

    it "should not convert underscores" do
      expect("hello_world".to_slug.normalize).to eql("hello_world")
    end

    it "should work with non roman chars" do
      expect("検 索".to_slug.normalize).to eql("検-索")
    end

    context "with to_ascii option" do
      it "should approximate and strip non ascii" do
        ss = "カタカナ: katakana is über cool".to_slug
        expect(ss.normalize(:to_ascii => true)).to eql("katakana-is-uber-cool")
      end
    end
  end

  describe "#truncate_bytes" do
    it "should by byte length" do
      expect("üa".to_slug.truncate_bytes(2)).to eql("ü")
      expect("üa".to_slug.truncate_bytes(1)).to eql("")
      expect("üa".to_slug.truncate_bytes(100)).to eql("üa")
      expect("üéøá".to_slug.truncate_bytes(3)).to eql("ü")
    end
  end

  describe "#truncate" do
    it "should truncate by char length" do
      expect("üa".to_slug.truncate(2)).to eql("üa")
      expect("üa".to_slug.truncate(1)).to eql("ü")
      expect("üa".to_slug.truncate(100)).to eql("üa")
    end
  end

  describe "#with_dashes" do
    it "should not change byte size when replacing spaces" do
      expect("".to_slug.with_dashes.bytesize).to eql(0)
      expect(" ".to_slug.with_dashes.bytesize).to eql(1)
      expect("-abc-".to_slug.with_dashes.bytesize).to eql(5)
      expect(" abc ".to_slug.with_dashes.bytesize).to eql(5)
      expect(" a  bc ".to_slug.with_dashes.bytesize).to eql(7)
    end
  end

  describe "#to_ruby_method" do
    it "should get a string suitable for use as a ruby method" do
      expect("¿¿¿hello... world???".to_slug.to_ruby_method).to eql("hello_world?")
      expect("カタカナ: katakana is über cool".to_slug.to_ruby_method).to eql("katakana_is_uber_cool")
      expect("カタカナ: katakana is über cool!".to_slug.to_ruby_method).to eql("katakana_is_uber_cool!")
      expect("カタカナ: katakana is über cool".to_slug.to_ruby_method(false)).to eql("katakana_is_uber_cool")
    end

    it "should optionally remove trailing punctuation" do
      expect("¿¿¿hello... world???".to_slug.to_ruby_method(false)).to eql("hello_world")
    end

    it "should raise an error when it would generate an impossible method name" do
      # "1".to_identifier.to_ruby_method
      expect {"1".to_identifier.to_ruby_method}.to raise_error(Babosa::Identifier::Error)
    end

    it "should raise Babosa::Error error when the string is nil" do
      expect { "".to_slug.to_ruby_method }.to raise_error(Babosa::Identifier::Error)
    end
  end
end
