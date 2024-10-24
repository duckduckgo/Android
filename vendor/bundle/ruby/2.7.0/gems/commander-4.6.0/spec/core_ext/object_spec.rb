# frozen_string_literal: true

require 'spec_helper'

describe Object do
  describe '#get_binding' do
    it 'should return the objects binding' do
      expect(-> {}.get_binding).to be_instance_of(Binding)
    end
  end

  describe '#method_missing' do
    it 'should preserve its original behavior for missing methods' do
      expect { send(:i_am_a_missing_method) }.to raise_error(NoMethodError)
    end

    it 'should preserve its original behavior for missing variables' do
      expect { i_am_a_missing_variable }.to raise_error(NameError)
    end
  end
end
