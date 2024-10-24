# -*- encoding: utf-8 -*-
# stub: fastlane-plugin-property_file_read 1.0.1 ruby lib

Gem::Specification.new do |s|
  s.name = "fastlane-plugin-property_file_read".freeze
  s.version = "1.0.1"

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["United Classifieds".freeze]
  s.date = "2017-09-29"
  s.email = "unitedclassifiedsapps@gmail.com".freeze
  s.homepage = "https://github.com/unitedclassifiedsapps/fastlane-plugin-property_file_read".freeze
  s.licenses = ["MIT".freeze]
  s.rubygems_version = "3.1.4".freeze
  s.summary = "Reads property file into dictionary".freeze

  s.installed_by_version = "3.1.4" if s.respond_to? :installed_by_version

  if s.respond_to? :specification_version then
    s.specification_version = 4
  end

  if s.respond_to? :add_runtime_dependency then
    s.add_development_dependency(%q<pry>.freeze, [">= 0"])
    s.add_development_dependency(%q<bundler>.freeze, [">= 0"])
    s.add_development_dependency(%q<rspec>.freeze, [">= 0"])
    s.add_development_dependency(%q<rake>.freeze, [">= 0"])
    s.add_development_dependency(%q<rubocop>.freeze, [">= 0"])
    s.add_development_dependency(%q<simplecov>.freeze, [">= 0"])
    s.add_development_dependency(%q<fastlane>.freeze, [">= 2.53.1"])
  else
    s.add_dependency(%q<pry>.freeze, [">= 0"])
    s.add_dependency(%q<bundler>.freeze, [">= 0"])
    s.add_dependency(%q<rspec>.freeze, [">= 0"])
    s.add_dependency(%q<rake>.freeze, [">= 0"])
    s.add_dependency(%q<rubocop>.freeze, [">= 0"])
    s.add_dependency(%q<simplecov>.freeze, [">= 0"])
    s.add_dependency(%q<fastlane>.freeze, [">= 2.53.1"])
  end
end
