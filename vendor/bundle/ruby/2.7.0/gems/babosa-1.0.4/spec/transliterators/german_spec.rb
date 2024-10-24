# encoding: utf-8
require File.expand_path("../../spec_helper", __FILE__)

describe Babosa::Transliterator::German do

  let(:t) { described_class.instance }
  it_behaves_like "a latin transliterator"

  it "should transliterate Eszett" do
    expect(t.transliterate("ß")).to eql("ss")
  end

  it "should transliterate vowels with umlauts" do
    expect(t.transliterate("üöä")).to eql("ueoeae")
  end

end