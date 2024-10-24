require "xcpretty/ansi"

module XCPretty
  describe ANSI do
    include ANSI

    before do
      self.colorize = true
      @text = "This is the PARTY"
    end

    describe "color helpers" do
      it "colors text red" do
        red(@text).should == "\e[31m#{@text}\e[0m"
      end

      it "formats text bold" do
        white(@text).should == "\e[39;1m#{@text}\e[0m"
      end

      it "colors text green" do
        green(@text).should == "\e[32;1m#{@text}\e[0m"
      end

      it "colors text cyan" do
        cyan(@text).should == "\e[36m#{@text}\e[0m"
      end
    end

    describe "custom color mixing" do
      it "can mix random known colors" do
        ansi_parse(@text, :yellow, :underline).should == "\e[33;4m#{@text}\e[0m"
      end
    end

    describe "debug helpers" do
      it "can remove formatting from text" do
        strip("\e[33;4m#{@text}\e[0m").should == @text
      end

      it "can list known applied effects" do
        applied_effects("\e[33;1m#{@text}\e[0m").should == [:yellow, :bold]
      end
    end
  end
end

