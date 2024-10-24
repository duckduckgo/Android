# encoding: utf-8
require File.expand_path("../spec_helper", __FILE__)

PROXIES = [Babosa::UTF8::DumbProxy, Babosa::UTF8::ActiveSupportProxy, Babosa::UTF8::UnicodeProxy]
PROXIES << Babosa::UTF8::JavaProxy if Babosa.jruby15?

PROXIES.each do |proxy|

  describe proxy do

    around do |example|
      begin
        old_proxy = Babosa::Identifier.utf8_proxy
        Babosa::Identifier.utf8_proxy = proxy
        example.run
      ensure
        Babosa::Identifier.utf8_proxy = old_proxy
      end
    end

    describe "#normalize_utf8" do
      it "should normalize to canonical composed" do
        # ÅÉÎØÜ
        uncomposed_bytes  = [65, 204, 138, 69, 204, 129, 73, 204, 130, 195, 152, 85, 204, 136]
        composed_bytes    = [195, 133, 195, 137, 195, 142, 195, 152, 195, 156]
        uncomposed_string = uncomposed_bytes.pack("C*").unpack("U*").pack("U*")
        expect(proxy.normalize_utf8(uncomposed_string).unpack("C*")).to eql(composed_bytes)
      end
    end

    describe "#upcase" do
      it "should upcase the string" do
        expect(proxy.upcase("åéîøü")).to eql("ÅÉÎØÜ")
        expect("åéîøü".to_identifier.upcase).to eql("ÅÉÎØÜ")
      end
    end

    describe "#downcase" do
      it "should downcase the string" do
        expect(proxy.downcase("ÅÉÎØÜ")).to eql("åéîøü")
        expect("ÅÉÎØÜ".to_identifier.downcase).to eql("åéîøü")
      end
    end

    describe 'tidy_bytes' do
      it 'should fix invalid UTF-8 strings' do
        expect(proxy.tidy_bytes("\x93abc")).to eq('“abc')
      end
    end

  end
end