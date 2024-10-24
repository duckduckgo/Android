require 'xcpretty/syntax'

module XCPretty
  describe Syntax do
    it 'syntax highlights given code' do
      code = 'self.color = [UIColor redColor];'
      snippet = Snippet.new(code, 'test.m')
      output = Syntax.highlight(snippet)

      stripped_output = output.gsub(/(?:(?:\u001b\[)|\u009b)(?:(?:[0-9]{1,3})?(?:;[0-9]{0,3})*?[A-M|f-m])|\u001b[A-M]/, '')
      stripped_output.should == code
    end

    it 'uses Objective-C lexer for Objective-C' do
      Syntax.find_lexer('test.m', '').should == Rouge::Lexers::ObjectiveC
      Syntax.find_lexer('test.h', '').should == Rouge::Lexers::ObjectiveC
    end

    it 'uses Swift lexer for Swift' do
      Syntax.find_lexer('test.swift', '').should == Rouge::Lexers::Swift
    end

    it 'uses Ruby lexer for Ruby' do
      Syntax.find_lexer('test.rb', '').should == Rouge::Lexers::Ruby
      Syntax.find_lexer('test.ruby', '').should == Rouge::Lexers::Ruby
    end

    it 'uses C++ lexer for C++' do
      Syntax.find_lexer('test.cpp', '').should == Rouge::Lexers::Cpp
      Syntax.find_lexer('test.cc', '').should == Rouge::Lexers::Cpp
      Syntax.find_lexer('test.c++', '').should == Rouge::Lexers::Cpp
      Syntax.find_lexer('test.cxx', '').should == Rouge::Lexers::Cpp
      Syntax.find_lexer('test.hpp', '').should == Rouge::Lexers::Cpp
      Syntax.find_lexer('test.h++', '').should == Rouge::Lexers::Cpp
      Syntax.find_lexer('test.hxx', '').should == Rouge::Lexers::Cpp
    end
  end
end

