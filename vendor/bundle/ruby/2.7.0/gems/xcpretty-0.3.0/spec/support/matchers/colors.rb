require 'rspec' unless Object.const_defined? :RSpec

RSpec::Matchers.define :be_colored do |expected|

  def effects_string(actual)
    effects = applied_effects(actual).join(' and ')
    effects.length > 0 ? effects : "unformatted"
  end

  match do |actual|
    applied_effects(actual).include? expected.to_sym
  end

  failure_message_for_should do |actual|
    "expected that '#{strip(actual)}' would be colored #{expected}, but was #{effects_string(actual)}"
  end
  failure_message_for_should_not do |actual|
    "expected that #{strip(actual)} would not be colored #{expected}, but was #{effects_string(actual)}"
  end
end

