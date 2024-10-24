require 'rake/testtask'

task :default => :test

Rake::TestTask.new do |t|
  t.libs << 'lib'
  t.pattern = 'test/**/*_test.rb'
  t.verbose = false
end

begin
  require 'mg'
  MG.new("colored.gemspec")
rescue LoadError
  abort "Please `gem install mg`"
end
