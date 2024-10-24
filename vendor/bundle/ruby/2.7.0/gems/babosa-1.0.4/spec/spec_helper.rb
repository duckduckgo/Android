# coding: utf-8

if ENV['COV']
  require 'simplecov'
  SimpleCov.start
end

require 'bundler/setup'
require 'babosa'

shared_examples_for "a latin transliterator" do
  let(:t) { described_class.instance }

  it "should transliterate latin characters" do
    string = (0xC0..0x17E).to_a.pack("U*")
    expect(t.transliterate(string)).to match(/[\x0-\x7f]/)
  end
end

shared_examples_for "a cyrillic transliterator" do
  let(:t) { described_class.instance }

  it "should transliterate cyrillic characters" do
    string = "Славься, Отечество наше свободное"
    expect(t.transliterate(string)).to match(/[\x0-\x7f]/)
  end
end

shared_examples_for "a greek transliterator" do
  let(:t) { described_class.instance }

  it "should transliterate greek characters" do
    string = "Γερμανία"
    expect(t.transliterate(string)).to match(/[\x0-\x7f]/)
  end
end

shared_examples_for "a hindi transliterator" do
  let(:t) { described_class.instance }

  it "should transliterate hindi characters" do
    string = "आदित्य तापड़िया"
    expect(t.transliterate(string)).to match(/[\x0-\x7f]/)
  end
end