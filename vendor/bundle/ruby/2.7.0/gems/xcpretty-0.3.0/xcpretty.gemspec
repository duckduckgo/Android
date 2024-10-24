# coding: utf-8
lib = File.expand_path('../lib', __FILE__)
$LOAD_PATH.unshift(lib) unless $LOAD_PATH.include?(lib)
require 'xcpretty/version'

Gem::Specification.new do |spec|
  spec.name          = "xcpretty"
  spec.version       = XCPretty::VERSION
  spec.authors       = ["Marin Usalj", "Delisa Mason"]
  spec.email         = ["marin2211@gmail.com", "iskanamagus@gmail.com"]
  spec.required_ruby_version = '>= 2.0.0'
  spec.description =
    %q{
    Xcodebuild formatter designed to be piped with `xcodebuild`,
    and thus keeping 100% compatibility.

    It has modes for CI, running tests (RSpec dot-style),
    and it can also mine Bitcoins.
    }
  spec.summary       = %q(xcodebuild formatter done right)
  spec.homepage      = "https://github.com/supermarin/xcpretty"
  spec.license       = "MIT"

  spec.files         = `git ls-files`.split($/)
  spec.executables   = spec.files.grep(%r{^bin/}) { |f| File.basename(f) }
  spec.test_files    = spec.files.grep(%r{^(test|spec|features)/})
  spec.require_paths = ["lib"]

  spec.add_dependency 'rouge', '~> 2.0.7'

  spec.add_development_dependency "bundler", "~> 1.3"
  spec.add_development_dependency "rake"
  spec.add_development_dependency "rubocop", "~> 0.34.0"
  spec.add_development_dependency "rspec", "2.99.0"
  spec.add_development_dependency "cucumber", "~> 1.0"
end

