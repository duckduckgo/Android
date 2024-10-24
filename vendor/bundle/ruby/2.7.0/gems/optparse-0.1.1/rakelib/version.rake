class << (helper = Bundler::GemHelper.instance)
  def mainfile
    "lib/#{File.basename(gemspec.loaded_from, ".gemspec")}.rb"
  end

  def update_version
    File.open(mainfile, "r+b") do |f|
      d = f.read
      if d.sub!(/^(\s*OptionParser::Version\s*=\s*)".*"/) {$1 + gemspec.version.to_s.dump}
        f.rewind
        f.truncate(0)
        f.print(d)
      end
    end
  end

  def commit_bump
    sh(%W[git -C #{File.dirname(gemspec.loaded_from)} commit -m bump\ up\ to\ #{gemspec.version}
          #{mainfile}])
  end

  def version=(v)
    gemspec.version = v
    update_version
    commit_bump
  end
end

major, minor, teeny = helper.gemspec.version.segments

task "bump:teeny" do
  helper.version = Gem::Version.new("#{major}.#{minor}.#{teeny+1}")
end

task "bump:minor" do
  helper.version = Gem::Version.new("#{major}.#{minor+1}.0")
end

task "bump:major" do
  helper.version = Gem::Version.new("#{major+1}.0.0")
end

task "bump" => "bump:teeny"

task "tag" do
  helper.__send__(:tag_version)
end
