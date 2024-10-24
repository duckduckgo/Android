# frozen_string_literal: true

$LOAD_PATH.push File.expand_path('lib', __dir__)
require 'commander/version'

Gem::Specification.new do |s|
  s.name        = 'commander'
  s.version     = Commander::VERSION
  s.authors     = ['TJ Holowaychuk', 'Gabriel Gilder']
  s.email       = ['gabriel@gabrielgilder.com']
  s.license     = 'MIT'
  s.homepage    = 'https://github.com/commander-rb/commander'
  s.summary     = 'The complete solution for Ruby command-line executables'
  s.description = 'The complete solution for Ruby command-line executables. Commander bridges the gap between other terminal related libraries you know and love (OptionParser, HighLine), while providing many new features, and an elegant API.'
  s.metadata    = {
    'bug_tracker_uri' => "#{s.homepage}/issues",
    'changelog_uri' => "#{s.homepage}/blob/master/History.rdoc",
    'documentation_uri' => "https://www.rubydoc.info/gems/commander/#{s.version}",
    'homepage_uri' => s.homepage,
    'source_code_uri' => "#{s.homepage}/tree/v#{s.version}",
  }
  s.required_ruby_version = '>= 2.4'

  s.files         = `git ls-files`.split("\n")
  s.test_files    = `git ls-files -- {test,spec,features}/*`.split("\n")
  s.executables   = `git ls-files -- bin/*`.split("\n").map { |f| File.basename(f) }
  s.require_paths = ['lib']

  s.add_runtime_dependency('highline', '~> 2.0.0')

  s.add_development_dependency('rake')
  s.add_development_dependency('rspec', '~> 3.2')
  s.add_development_dependency('rubocop', '~> 1.12.1')
  s.add_development_dependency('simplecov')
end
