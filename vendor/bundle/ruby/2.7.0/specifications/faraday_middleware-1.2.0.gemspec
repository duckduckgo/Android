# -*- encoding: utf-8 -*-
# stub: faraday_middleware 1.2.0 ruby lib

Gem::Specification.new do |s|
  s.name = "faraday_middleware".freeze
  s.version = "1.2.0"

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Erik Michaels-Ober".freeze, "Wynn Netherland".freeze]
  s.date = "2021-10-14"
  s.email = ["sferik@gmail.com".freeze, "wynn.netherland@gmail.com".freeze]
  s.homepage = "https://github.com/lostisland/faraday_middleware".freeze
  s.licenses = ["MIT".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.3".freeze)
  s.rubygems_version = "3.1.4".freeze
  s.summary = "Various middleware for Faraday".freeze

  s.installed_by_version = "3.1.4" if s.respond_to? :installed_by_version

  if s.respond_to? :specification_version then
    s.specification_version = 4
  end

  if s.respond_to? :add_runtime_dependency then
    s.add_runtime_dependency(%q<faraday>.freeze, ["~> 1.0"])
  else
    s.add_dependency(%q<faraday>.freeze, ["~> 1.0"])
  end
end
