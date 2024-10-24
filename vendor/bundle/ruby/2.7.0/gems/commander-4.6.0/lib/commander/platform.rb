# frozen_string_literal: true

module Commander
  module Platform
    def self.jruby?
      defined?(RUBY_ENGINE) && (RUBY_ENGINE == 'jruby')
    end
  end
end
