# encoding: utf-8
require File.expand_path("../../spec_helper", __FILE__)

describe Babosa::Transliterator::Serbian do

  let(:t) { described_class.instance }
  it_behaves_like "a latin transliterator"
  it_behaves_like "a cyrillic transliterator"

  it "should transliterate Latin characters" do
    examples = {
      "Ðorđe"  => "Djordje",
      "Inđija" => "Indjija",
      "Četiri" => "Chetiri",
      "četiri" => "chetiri",
      "Škola"  => "Shkola",
      "škola"  => "shkola",
      "Ђорђе"  => "Djordje",
      "Инђија" => "Indjija",
      "Школа"  => "Shkola",
    }
    examples.each {|k, v| expect(t.transliterate(k)).to eql(v)}
  end

end