# Copyright (c) 2015  Radek Pazdera
# Distributed under the MIT License

# Tests for the extensions of the String class

require 'word_wrap/core_ext'

describe WordWrap do
  describe "core extensions" do
    it "fit as a part of String interface" do
      text =<<EOF
Try-hard 3 wolf moon vinyl.

Mumblecore letterpress iPhone.
EOF

      expected =<<EOF
Try-hard 3 wolf moon
vinyl.

Mumblecore
letterpress iPhone.
EOF
      expect(text.fit(20)).to eql expected
    end

    it "wraps in-place" do
      text = "0123456789 01234 0123456"
      expected =<<EOF
0123456789
01234
0123456
EOF
      text.wrap! 10
      expect(text).to eql expected
    end

    it "fits in-place" do
      text = "0123456789 01234 0123456"
      expected =<<EOF
0123456789
01234
0123456
EOF
      text.fit! 10
      expect(text).to eql expected
    end
  end
end
