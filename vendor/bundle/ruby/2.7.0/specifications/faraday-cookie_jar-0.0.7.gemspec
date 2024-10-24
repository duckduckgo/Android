# -*- encoding: utf-8 -*-
# stub: faraday-cookie_jar 0.0.7 ruby lib

Gem::Specification.new do |s|
  s.name = "faraday-cookie_jar".freeze
  s.version = "0.0.7"

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Tatsuhiko Miyagawa".freeze]
  s.date = "2020-09-02"
  s.description = "Cookie jar middleware for Faraday".freeze
  s.email = ["miyagawa@bulknews.net".freeze]
  s.homepage = "https://github.com/miyagawa/faraday-cookie_jar".freeze
  s.licenses = ["MIT".freeze]
  s.rubygems_version = "3.1.4".freeze
  s.summary = "Manages client-side cookie jar for Faraday HTTP client".freeze

  s.installed_by_version = "3.1.4" if s.respond_to? :installed_by_version

  if s.respond_to? :specification_version then
    s.specification_version = 4
  end

  if s.respond_to? :add_runtime_dependency then
    s.add_runtime_dependency(%q<faraday>.freeze, [">= 0.8.0"])
    s.add_runtime_dependency(%q<http-cookie>.freeze, ["~> 1.0.0"])
    s.add_development_dependency(%q<bundler>.freeze, ["~> 1.3"])
    s.add_development_dependency(%q<rake>.freeze, [">= 0"])
    s.add_development_dependency(%q<rspec>.freeze, ["~> 3.2"])
    s.add_development_dependency(%q<sinatra>.freeze, [">= 0"])
    s.add_development_dependency(%q<sham_rack>.freeze, [">= 0"])
  else
    s.add_dependency(%q<faraday>.freeze, [">= 0.8.0"])
    s.add_dependency(%q<http-cookie>.freeze, ["~> 1.0.0"])
    s.add_dependency(%q<bundler>.freeze, ["~> 1.3"])
    s.add_dependency(%q<rake>.freeze, [">= 0"])
    s.add_dependency(%q<rspec>.freeze, ["~> 3.2"])
    s.add_dependency(%q<sinatra>.freeze, [">= 0"])
    s.add_dependency(%q<sham_rack>.freeze, [">= 0"])
  end
end
