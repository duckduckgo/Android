# -*- encoding: utf-8 -*-
# stub: security 0.1.3 ruby lib

Gem::Specification.new do |s|
  s.name = "security".freeze
  s.version = "0.1.3"

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Mattt Thompson".freeze]
  s.date = "2014-08-05"
  s.description = "Library for interacting with the Mac OS X Keychain".freeze
  s.email = "m@mattt.me".freeze
  s.homepage = "http://mattt.me".freeze
  s.rubygems_version = "3.1.4".freeze
  s.summary = "Security".freeze

  s.installed_by_version = "3.1.4" if s.respond_to? :installed_by_version

  if s.respond_to? :specification_version then
    s.specification_version = 4
  end

  if s.respond_to? :add_runtime_dependency then
    s.add_development_dependency(%q<rspec>.freeze, ["~> 0.6.1"])
    s.add_development_dependency(%q<rake>.freeze, ["~> 0.9.2"])
  else
    s.add_dependency(%q<rspec>.freeze, ["~> 0.6.1"])
    s.add_dependency(%q<rake>.freeze, ["~> 0.9.2"])
  end
end
