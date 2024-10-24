# encoding: utf-8
require File.expand_path("../../spec_helper", __FILE__)

describe Babosa::Transliterator::Base do

  let(:t) {Babosa::Transliterator::Base.instance}

  it "should transliterate 'smart' quotes" do
    expect(t.transliterate("’")).to eql("'")
  end

  it "should transliterate non-breaking spaces" do
    expect(t.transliterate("\xc2\xa0")).to eql(" ")
  end

end