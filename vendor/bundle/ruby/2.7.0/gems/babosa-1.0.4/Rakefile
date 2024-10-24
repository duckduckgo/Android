require "rubygems"
require "rake/testtask"
require "rake/clean"
require "rubygems/package_task"

task :default => :spec
task :test    => :spec

CLEAN << "pkg" << "doc" << "coverage" << ".yardoc"

begin
  require "yard"
  YARD::Rake::YardocTask.new do |t|
    t.options = ["--output-dir=doc"]
  end
rescue LoadError
end

begin
  desc "Run SimpleCov"
  task :coverage do
    ENV["COV"] = "true"
    Rake::Task["spec"].execute
  end
rescue LoadError
end

gemspec = File.expand_path("../babosa.gemspec", __FILE__)
if File.exist? gemspec
  Gem::PackageTask.new(eval(File.read(gemspec))) { |pkg| }
end

require 'rspec/core/rake_task'
RSpec::Core::RakeTask.new(:spec)
