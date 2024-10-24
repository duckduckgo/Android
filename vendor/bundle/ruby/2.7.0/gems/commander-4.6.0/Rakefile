# frozen_string_literal: true

require 'bundler/gem_tasks'
require 'rspec/core/rake_task'
require 'rubocop/rake_task'

desc 'Run specs'
RSpec::Core::RakeTask.new do |t|
  t.verbose = false
  t.rspec_opts = '--color --order random'
end

RuboCop::RakeTask.new

task default: %i[spec rubocop]
