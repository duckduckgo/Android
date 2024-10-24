require 'xcpretty/snippet'

module XCPretty

  describe Snippet do

    it "gets the snippet out of the file path" do
      path = File.expand_path('spec/fixtures/NSStringTests.m:36')
      Snippet.from_filepath(path).contents.should ==
<<-EOS
    it(@"-split: splits with delimiter string, not just a char", ^{
        [[[@"one / two / three" split:@" / "] should] equal:@[@"one", @"two", @"three"]];
    });
EOS
    end

    it 'saves the file path' do
      path = File.expand_path('spec/fixtures/NSStringTests.m:36')
      Snippet.from_filepath(path).file_path.should == path
    end

    it "doesn't crash when there's nothing to read" do
      path = File.expand_path('spec/fixtures/NSStringTests.m:64')
      Snippet.from_filepath(path).contents.should == "\nSPEC_END\n"
    end

    it "doesn't crash if file path is invalid" do
      path = 'invalid-path'
      Snippet.from_filepath(path).contents.should == ''
    end

    it "doesn't crash if a failure is on the first line" do
      path = File.expand_path('spec/fixtures/NSStringTests.m:0')
      Snippet.from_filepath(path).contents.should ==
        "//\n//  NSStringTests.m\n//  SampleProject\n"
    end

    it "doesn't crash if the file has only 1 line" do
      path = File.expand_path('spec/fixtures/oneliner.m:0')
      Snippet.from_filepath(path).contents.should ==
         "[[[@1 should] equal] @3];\n"
    end

  end
end

