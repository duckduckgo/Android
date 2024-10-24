require 'xcpretty'
require 'tempfile'

module XCPretty
  describe JUnit do
    before(:each) do
      @reporter_file = Tempfile.new('junit')
      @formatter = JUnit.new(path: @reporter_file.path)
    end

    it "has name attribute in root node" do
      test_name = "ReactiveCocoaTests.xctest"
      @formatter.format_test_run_started(test_name)
      @formatter.finish
      document = REXML::Document.new(@reporter_file)
      document.root.attributes['name'].should == test_name
    end
  end
end

