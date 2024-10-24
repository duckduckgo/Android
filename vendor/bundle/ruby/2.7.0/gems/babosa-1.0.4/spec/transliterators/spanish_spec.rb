# encoding: utf-8
require File.expand_path("../../spec_helper", __FILE__)

describe Babosa::Transliterator::Spanish do

  let(:t) { described_class.instance }
  it_behaves_like "a latin transliterator"

  it "should transliterate ñ" do
    expect(t.transliterate("ñ")).to eql("ni")
  end

end