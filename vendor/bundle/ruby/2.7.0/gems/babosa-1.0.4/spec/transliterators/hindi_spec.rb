# encoding: utf-8
require File.expand_path("../../spec_helper", __FILE__)

describe Babosa::Transliterator::Hindi do

  let(:t) { described_class.instance }
  it_behaves_like "a hindi transliterator"

  it "should transliterate hindi characters" do
    examples = {
      "आदित्य"  => "aadity",
      "सबरीमाला करवाना पायसम"   => "sbriimaalaa krvaanaa paaysm",
      "सक्रांति आँख" => "skraanti aankh"
    }
    examples.each {|k, v| expect(t.transliterate(k)).to eql(v)}
  end
end
