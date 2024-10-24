# -*- encoding: utf-8 -*-
# stub: xcpretty 0.3.0 ruby lib

Gem::Specification.new do |s|
  s.name = "xcpretty".freeze
  s.version = "0.3.0"

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Marin Usalj".freeze, "Delisa Mason".freeze]
  s.date = "2018-08-16"
  s.description = "\n    Xcodebuild formatter designed to be piped with `xcodebuild`,\n    and thus keeping 100% compatibility.\n\n    It has modes for CI, running tests (RSpec dot-style),\n    and it can also mine Bitcoins.\n    ".freeze
  s.email = ["marin2211@gmail.com".freeze, "iskanamagus@gmail.com".freeze]
  s.executables = ["xcpretty".freeze]
  s.files = ["bin/xcpretty".freeze]
  s.homepage = "https://github.com/supermarin/xcpretty".freeze
  s.licenses = ["MIT".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.0.0".freeze)
  s.rubygems_version = "3.1.4".freeze
  s.summary = "xcodebuild formatter done right".freeze

  s.installed_by_version = "3.1.4" if s.respond_to? :installed_by_version

  if s.respond_to? :specification_version then
    s.specification_version = 4
  end

  if s.respond_to? :add_runtime_dependency then
    s.add_runtime_dependency(%q<rouge>.freeze, ["~> 2.0.7"])
    s.add_development_dependency(%q<bundler>.freeze, ["~> 1.3"])
    s.add_development_dependency(%q<rake>.freeze, [">= 0"])
    s.add_development_dependency(%q<rubocop>.freeze, ["~> 0.34.0"])
    s.add_development_dependency(%q<rspec>.freeze, ["= 2.99.0"])
    s.add_development_dependency(%q<cucumber>.freeze, ["~> 1.0"])
  else
    s.add_dependency(%q<rouge>.freeze, ["~> 2.0.7"])
    s.add_dependency(%q<bundler>.freeze, ["~> 1.3"])
    s.add_dependency(%q<rake>.freeze, [">= 0"])
    s.add_dependency(%q<rubocop>.freeze, ["~> 0.34.0"])
    s.add_dependency(%q<rspec>.freeze, ["= 2.99.0"])
    s.add_dependency(%q<cucumber>.freeze, ["~> 1.0"])
  end
end
