require 'bundler/gem_tasks'

task :kick do
  sh 'bundle exec kicker -r ruby'
end

task :spec do
  sh 'bundle exec rspec --color --format=doc'
end

task :cucumber do
  sh 'bundle exec cucumber'
end

task :lint do
  sh 'bundle exec rubocop'
end

