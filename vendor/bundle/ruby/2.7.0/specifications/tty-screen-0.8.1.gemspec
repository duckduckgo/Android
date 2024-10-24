# -*- encoding: utf-8 -*-
# stub: tty-screen 0.8.1 ruby lib

Gem::Specification.new do |s|
  s.name = "tty-screen".freeze
  s.version = "0.8.1"

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.metadata = { "allowed_push_host" => "https://rubygems.org", "bug_tracker_uri" => "https://github.com/piotrmurach/tty-screen/issues", "changelog_uri" => "https://github.com/piotrmurach/tty-screen/blob/master/CHANGELOG.md", "documentation_uri" => "https://www.rubydoc.info/gems/tty-screen", "homepage_uri" => "https://ttytoolkit.org", "source_code_uri" => "https://github.com/piotrmurach/tty-screen" } if s.respond_to? :metadata=
  s.require_paths = ["lib".freeze]
  s.authors = ["Piotr Murach".freeze]
  s.date = "2020-07-17"
  s.description = "Terminal screen size detection which works on Linux, OS X and Windows/Cygwin platforms and supports MRI, JRuby, TruffleRuby and Rubinius interpreters.".freeze
  s.email = ["piotr@piotrmurach.com".freeze]
  s.extra_rdoc_files = ["README.md".freeze, "CHANGELOG.md".freeze, "LICENSE.txt".freeze]
  s.files = ["CHANGELOG.md".freeze, "LICENSE.txt".freeze, "README.md".freeze]
  s.homepage = "https://ttytoolkit.org".freeze
  s.licenses = ["MIT".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.0.0".freeze)
  s.rubygems_version = "3.1.4".freeze
  s.summary = "Terminal screen size detection which works on Linux, OS X and Windows/Cygwin platforms and supports MRI, JRuby, TruffleRuby and Rubinius interpreters.".freeze

  s.installed_by_version = "3.1.4" if s.respond_to? :installed_by_version

  if s.respond_to? :specification_version then
    s.specification_version = 4
  end

  if s.respond_to? :add_runtime_dependency then
    s.add_development_dependency(%q<rake>.freeze, [">= 0"])
    s.add_development_dependency(%q<rspec>.freeze, [">= 3.0"])
  else
    s.add_dependency(%q<rake>.freeze, [">= 0"])
    s.add_dependency(%q<rspec>.freeze, [">= 3.0"])
  end
end
