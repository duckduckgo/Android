# coding: utf-8
lib = File.expand_path('../lib', __FILE__)
$LOAD_PATH.unshift(lib) unless $LOAD_PATH.include?(lib)
require 'word_wrap/version'

Gem::Specification.new do |spec|
  spec.name          = "word_wrap"
  spec.version       = WordWrap::VERSION
  spec.authors       = ["Radek Pazdera"]
  spec.email         = ["radek@pazdera.co.uk"]
  spec.summary       = %q{Simple tool for word-wrapping text}
  spec.description   = %q{As simple as it gets CLI tool for word-wrapping
                          plain-text. You can also use the library in your
                          Ruby scripts. Check out the sources for details.}
  spec.homepage      = "https://github.com/pazdera/word_wrap"
  spec.license       = "MIT"

  spec.files         = `git ls-files`.split($/)
  spec.executables   = spec.files.grep(%r{^bin/}) { |f| File.basename(f) }
  spec.test_files    = spec.files.grep(%r{^(test|spec|features)/})
  spec.require_paths = ["lib"]

  spec.add_development_dependency "bundler", "~> 1.5"
  spec.add_development_dependency "rake", "~> 0"
  spec.add_development_dependency "rspec", "~> 0"
end
