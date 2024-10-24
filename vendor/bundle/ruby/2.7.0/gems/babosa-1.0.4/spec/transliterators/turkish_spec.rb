# encoding: utf-8
require File.expand_path("../../spec_helper", __FILE__)

describe Babosa::Transliterator::Turkish do

  let(:t) { described_class.instance }
  it_behaves_like "a latin transliterator"

  it "should transliterate various characters" do
    examples = {
      "Nâzım"          => "Nazim",
      "sükûnet"        => "sukunet",
      "millîleştirmek" => "millilestirmek",
      "mêmur"          => "memur",
      "lôkman"         => "lokman",
      "yoğurt"         => "yogurt",
      "şair"           => "sair",
      "İzmir"          => "Izmir",
      "yığın"          => "yigin",
      "çarşı"          => "carsi"
    }
    examples.each {|k, v| expect(t.transliterate(k)).to eql(v)}
  end
end
