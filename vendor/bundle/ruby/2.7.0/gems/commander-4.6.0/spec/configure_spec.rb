# frozen_string_literal: true

require 'spec_helper'
require 'commander/configure'

describe Commander do
  describe '.configure' do
    it 'calls the given block' do
      expect { Commander.configure { throw :block_called } }.to throw_symbol(:block_called)
    end

    describe 'called block' do
      before(:each) do
        allow(Commander::Runner.instance).to receive(:run!)
      end

      it 'provides Commander configuration methods' do
        Commander.configure do
          program :name, 'test'
        end

        expect(Commander::Runner.instance.program(:name)).to eq('test')
      end

      it 'passes all arguments to the block' do
        Commander.configure('foo') do |first_arg|
          program :name, first_arg
        end

        expect(Commander::Runner.instance.program(:name)).to eq('foo')
      end
    end

    it 'calls Runner#run! after calling the configuration block' do
      expect(Commander::Runner.instance).to receive(:run!)
      Commander.configure {}
    end
  end
end
