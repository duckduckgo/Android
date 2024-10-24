# -*- encoding: utf-8 -*-
# stub: tty-spinner 0.9.3 ruby lib

Gem::Specification.new do |s|
  s.name = "tty-spinner".freeze
  s.version = "0.9.3"

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.metadata = { "allowed_push_host" => "https://rubygems.org", "bug_tracker_uri" => "https://github.com/piotrmurach/tty-spinner/issues", "changelog_uri" => "https://github.com/piotrmurach/tty-spinner/blob/master/CHANGELOG.md", "documentation_uri" => "https://www.rubydoc.info/gems/tty-spinner", "homepage_uri" => "https://ttytoolkit.org", "source_code_uri" => "https://github.com/piotrmurach/tty-spinner" } if s.respond_to? :metadata=
  s.require_paths = ["lib".freeze]
  s.authors = ["Piotr Murach".freeze]
  s.bindir = "exe".freeze
  s.date = "2020-01-28"
  s.description = "A terminal spinner for tasks that have non-deterministic time frame.".freeze
  s.email = ["piotr@piotrmurach.com".freeze]
  s.extra_rdoc_files = ["README.md".freeze, "CHANGELOG.md".freeze]
  s.files = ["CHANGELOG.md".freeze, "README.md".freeze]
  s.homepage = "https://ttytoolkit.org".freeze
  s.licenses = ["MIT".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.0.0".freeze)
  s.rubygems_version = "3.1.4".freeze
  s.summary = "A terminal spinner for tasks that have non-deterministic time frame.".freeze

  s.installed_by_version = "3.1.4" if s.respond_to? :installed_by_version

  if s.respond_to? :specification_version then
    s.specification_version = 4
  end

  if s.respond_to? :add_runtime_dependency then
    s.add_runtime_dependency(%q<tty-cursor>.freeze, ["~> 0.7"])
  else
    s.add_dependency(%q<tty-cursor>.freeze, ["~> 0.7"])
  end
end
