require "bundler"
Bundler.setup

gemspec = eval(File.read("security.gemspec"))

task :build => "#{gemspec.full_name}.gem"

file "#{gemspec.full_name}.gem" => gemspec.files + ["security.gemspec"] do
  system "gem build security.gemspec"
end
