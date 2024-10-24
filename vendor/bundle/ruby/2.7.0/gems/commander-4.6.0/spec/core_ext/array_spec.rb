# frozen_string_literal: true

require 'spec_helper'

describe Array do
  describe '#parse' do
    it 'should seperate a list of words into an array' do
      expect(Array.parse('just a test')).to eq(%w(just a test))
    end

    it 'should preserve escaped whitespace' do
      expect(Array.parse('just a\ test')).to eq(['just', 'a test'])
    end

    it 'should match %w behavior with multiple backslashes' do
      str = 'just a\\ test'
      expect(Array.parse(str)).to eq(['just', 'a test'])
    end
  end
end
