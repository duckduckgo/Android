# encoding: utf-8
require File.expand_path("../../spec_helper", __FILE__)

describe Babosa::Transliterator::Bulgarian do

  let(:t) { described_class.instance }
  it_behaves_like "a cyrillic transliterator"

  it "should transliterate Cyrillic characters" do
    examples = {
      "Ютия"    => "Iutiia",
      "Чушка"   => "Chushka",
      "кьорав"  => "kiorav",
      "Щъркел"  => "Shturkel",
      "полицай" => "policai"
    }
    examples.each {|k, v| expect(t.transliterate(k)).to eql(v)}
  end

end
