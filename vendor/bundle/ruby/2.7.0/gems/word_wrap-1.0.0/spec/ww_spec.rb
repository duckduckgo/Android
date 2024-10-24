# Copyright (c) 2014, 2015  Radek Pazdera
# Distributed under the MIT License

# Tests for the ww function

require 'word_wrap'

describe WordWrap do
  describe "#ww" do
    it "wraps a single line correctly" do
      text = "0123456789 01234 0123456"
      expected =<<EOF
0123456789
01234
0123456
EOF
      wrapped = WordWrap.ww(text, 10)
      expect(wrapped).to eql expected
    end

    it "works without breaks" do
      text =<<EOF
0123456789012345678 9
0123456 0123456
EOF

      expected =<<EOF
0123456789012345678
9
0123456
0123456
EOF
      wrapped = WordWrap.ww(text, 10)
      expect(wrapped).to eql expected
    end

    it "wraps two paragraphs" do
      text =<<EOF
Try-hard 3 wolf moon vinyl, authentic disrupt banh mi cliche fixie skateboard biodiesel chillwave before they sold out pop-up direct trade.

Mumblecore letterpress iPhone, Brooklyn pork belly distillery cray semiotics.
EOF

      expected =<<EOF
Try-hard 3 wolf
moon vinyl,
authentic disrupt
banh mi cliche
fixie skateboard
biodiesel chillwave
before they sold
out pop-up direct
trade.

Mumblecore
letterpress iPhone,
Brooklyn pork belly
distillery cray
semiotics.
EOF
      wrapped = WordWrap.ww(text, 20)
      expect(wrapped).to eql expected
    end

    it "wraps a partialy wrapped paragraph" do
      text =<<EOF
Try-hard 3 wolf moon vinyl,
authentic
disrupt banh mi cliche
fixie skateboard biodiesel chillwave
before they sold out pop-up direct trade.
EOF

      expected =<<EOF
Try-hard 3 wolf
moon vinyl,
authentic
disrupt banh mi
cliche
fixie skateboard
biodiesel chillwave
before they sold
out pop-up direct
trade.
EOF
      wrapped = WordWrap.ww(text, 20)
      expect(wrapped).to eql expected
    end

    it "wrapping keeps whitespace" do
      text =<<EOF

        word word word word word


word      word  word
word word   reallylong word

extremelylonganduglyword

    extremelylonganduglyword


EOF

      expected =<<EOF

        word word
word word word


word      word  word
word word
reallylong word

extremelylonganduglyword


extremelylonganduglyword


EOF
      wrapped = WordWrap.ww(text, 20)
      expect(wrapped).to eql expected
    end

    it "wrap as a part of String interface" do
      text =<<EOF
Try-hard 3 wolf moon vinyl.

Mumblecore letterpress iPhone.
EOF

      expected =<<EOF
Try-hard 3 wolf
moon vinyl.

Mumblecore
letterpress iPhone.
EOF
      expect(text.wrap(20)).to eql expected
    end

    it "fits a single line correctly" do
      text = "0123456789 01234 0123456"
      expected =<<EOF
0123456789
01234
0123456
EOF
      wrapped = WordWrap.ww(text, 10, true)
      expect(wrapped).to eql expected
    end

    it "works without breaks" do
      text =<<EOF
0123456789012345678 9
0123456 0123456
EOF

      expected =<<EOF
0123456789012345678
9 0123456
0123456
EOF
      wrapped = WordWrap.ww(text, 10, true)
      expect(wrapped).to eql expected
    end

    it "fits two paragraphs" do
      text =<<EOF
Try-hard 3 wolf moon vinyl, authentic disrupt banh mi cliche fixie skateboard biodiesel chillwave before they sold out pop-up direct trade.

Mumblecore letterpress iPhone, Brooklyn pork belly distillery cray semiotics.
EOF

      expected =<<EOF
Try-hard 3 wolf moon
vinyl, authentic
disrupt banh mi
cliche fixie
skateboard biodiesel
chillwave before
they sold out pop-up
direct trade.

Mumblecore
letterpress iPhone,
Brooklyn pork belly
distillery cray
semiotics.
EOF
      wrapped = WordWrap.ww(text, 20, true)
      expect(wrapped).to eql expected
    end

    it "fits a partialy wrapped paragraph" do
      text =<<EOF
Try-hard 3 wolf moon vinyl,
awesome
disrupt banh mi cliche
fixie skateboard biodiesel chillwave
before they sold out pop-up direct trade.
EOF

      expected =<<EOF
Try-hard 3 wolf moon
vinyl, awesome
disrupt banh mi
cliche fixie
skateboard biodiesel
chillwave before
they sold out pop-up
direct trade.
EOF
      wrapped = WordWrap.ww(text, 20, true)
      expect(wrapped).to eql expected
    end

    it "fitting keeps empty lines" do
      text =<<EOF

        word word word word word


word      word  word
word word   reallylong word

extremelylonganduglyword

    extremelylonganduglyword


EOF

      expected =<<EOF

word word word word
word


word word word word
word reallylong word

extremelylonganduglyword

extremelylonganduglyword


EOF
      wrapped = WordWrap.ww(text, 20, true)
      expect(wrapped).to eql expected
    end
  end
end
