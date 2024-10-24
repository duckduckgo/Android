# frozen_string_literal: true

require 'spec_helper'
require 'commander/methods'

describe Commander::Methods do
  it 'includes Commander::UI' do
    expect(subject.ancestors).to include(Commander::UI)
  end

  describe 'AskForClass' do
    it 'includes Commander::UI::AskForClass' do
      expect(subject.ancestors).to include(Commander::UI::AskForClass)
    end

    describe 'defining methods' do
      let(:terminal) { double }

      before do
        allow(terminal).to receive(:ask)
        @old_highline = HighLine.default_instance
        HighLine.default_instance = terminal
      end

      after do
        HighLine.default_instance = @old_highline
      end

      subject do
        Class.new do
          include Commander::UI::AskForClass
        end.new
      end

      it 'defines common "ask_for_*" methods' do
        expect(subject.respond_to?(:ask_for_float)).to be_truthy
      end

      it 'responds to "ask_for_*" methods for classes that implement #parse' do
        expect(subject.respond_to?(:ask_for_datetime)).to be_truthy
      end

      it 'fails "ask_for_*" method invocations without a prompt' do
        expect do
          subject.ask_for_datetime
        end.to raise_error(ArgumentError)
      end

      it 'implements "ask_for_*"' do
        expect(terminal).to receive(:ask)
        subject.ask_for_datetime('hi')
      end
    end
  end

  it 'includes Commander::Delegates' do
    expect(subject.ancestors).to include(Commander::Delegates)
  end

  it 'does not change the Object ancestors' do
    expect(Object.ancestors).not_to include(Commander::UI)
  end
end
