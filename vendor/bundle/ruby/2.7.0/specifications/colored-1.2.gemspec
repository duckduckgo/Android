# -*- encoding: utf-8 -*-
# stub: colored 1.2 ruby lib

Gem::Specification.new do |s|
  s.name = "colored".freeze
  s.version = "1.2"

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Chris Wanstrath".freeze]
  s.date = "2010-02-10"
  s.description = "  >> puts \"this is red\".red\n \n  >> puts \"this is red with a blue background (read: ugly)\".red_on_blue\n\n  >> puts \"this is red with an underline\".red.underline\n\n  >> puts \"this is really bold and really blue\".bold.blue\n\n  >> logger.debug \"hey this is broken!\".red_on_yellow     # in rails\n\n  >> puts Color.red \"This is red\" # but this part is mostly untested\n".freeze
  s.email = "chris@ozmm.org".freeze
  s.homepage = "http://github.com/defunkt/colored".freeze
  s.rubygems_version = "3.1.4".freeze
  s.summary = "Add some color to your life.".freeze

  s.installed_by_version = "3.1.4" if s.respond_to? :installed_by_version
end
