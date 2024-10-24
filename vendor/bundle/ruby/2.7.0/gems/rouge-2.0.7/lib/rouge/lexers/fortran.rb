# -*- coding: utf-8 -*- #
# vim: set ts=2 sw=2 et:

# TODO: Implement format list support.

module Rouge
  module Lexers
    class Fortran < RegexLexer
      title "Fortran"
      desc "Fortran 95 Programming Language"

      tag 'fortran'
      filenames '*.f90', '*.f95',
                '*.F90', '*.F95'
      mimetypes 'text/x-fortran'

      name = /[A-Z][_A-Z0-9]*/i
      kind_param = /(\d+|#{name})/
      exponent = /[ED][+-]?\d+/i

      def self.keywords
        # Fortran allows to omit whitespace between certain keywords...
        @keywords ||= Set.new %w(
          allocatable allocate assignment backspace block blockdata call case
          close common contains continue cycle data deallocate default
          dimension do elemental else elseif elsewhere end endblockdata enddo
          endfile endforall endfunction endif endinterface endmodule endprogram
          endselect endsubroutine endtype endwhere entry equivalence exit
          external forall format function go goto if implicit in include inout
          inquire intent interface intrinsic module namelist none nullify only
          open operator optional out parameter pointer print private procedure
          program public pure read recursive result return rewind save select
          selectcase sequence stop subroutine target then to type use where
          while write
        )
      end

      def self.types
        @types ||= Set.new %w(
          character complex double precision doubleprecision integer logical real
        )
      end

      def self.intrinsics
        @intrinsics ||= Set.new %w(
          abs achar acos adjustl adjustr aimag aint all allocated anint any
          asin associated atan atan2 bit_size btest ceiling char cmplx conjg
          cos cosh count cpu_time cshift date_and_time dble digits dim
          dot_product dprod eoshift epsilon exp exponent floor fraction huge
          iachar iand ibclr ibits ibset ichar ieor index int ior ishift ishiftc
          kind lbound len len_trim lge lgt lle llt log log10 logical matmul max
          maxexponent maxloc maxval merge min minexponent minloc minval mod
          modulo mvbits nearest nint not null pack precision present product
          radix random_number random_seed range real repeat reshape rrspacing
          scale scan selected_int_kind selected_real_kind set_exponent shape
          sign sin sinh size spacing spread sqrt sum system_clock tan tanh tiny
          transfer transpose trim ubound unpack verify
        )
      end

      state :root do
        rule /[\s\n]+/, Text::Whitespace
        rule /!.*$/, Comment::Single
        rule /^#.*$/, Comment::Preproc

        rule /::|[()\/;,:&]/, Punctuation

        # TODO: This does not take into account line continuation.
        rule /^(\s*)([0-9]+)\b/m do |m|
          token Text::Whitespace, m[1]
          token Name::Label, m[2]
        end

        # Format statements are quite a strange beast.
        # Better process them in their own state.
        rule /\b(FORMAT)(\s*)(\()/mi do |m|
          token Keyword, m[1]
          token Text::Whitespace, m[2]
          token Punctuation, m[3]
          push :format_spec
        end

        rule %r(
          [+-]? # sign
          (
            (\d+[.]\d*|[.]\d+)(#{exponent})?
            | \d+#{exponent} # exponent is mandatory
          )
          (_#{kind_param})? # kind parameter
        )xi, Num::Float

        rule /[+-]?\d+(_#{kind_param})?/i, Num::Integer
        rule /B'[01]+'|B"[01]+"/i, Num::Bin
        rule /O'[0-7]+'|O"[0-7]+"/i, Num::Oct
        rule /Z'[0-9A-F]+'|Z"[0-9A-F]+"/i, Num::Hex
        rule /(#{kind_param}_)?'/, Str::Single, :string_single
        rule /(#{kind_param}_)?"/, Str::Double, :string_double
        rule /[.](TRUE|FALSE)[.](_#{kind_param})?/i, Keyword::Constant

        rule %r{\*\*|//|==|/=|<=|>=|=>|[-+*/<>=%]}, Operator
        rule /\.(?:EQ|NE|LT|LE|GT|GE|NOT|AND|OR|EQV|NEQV|[A-Z]+)\./i, Operator::Word

        rule /#{name}/m do |m|
          match = m[0].downcase
          if self.class.keywords.include? match
            token Keyword
          elsif self.class.types.include? match
            token Keyword::Type
          elsif self.class.intrinsics.include? match
            token Name::Builtin
          else
            token Name
          end
        end

      end

      state :string_single do
        rule /[^']+/, Str::Single
        rule /''/, Str::Escape
        rule /'/, Str::Single, :pop!
      end

      state :string_double do
        rule /[^"]+/, Str::Double
        rule /""/, Str::Escape
        rule /"/, Str::Double, :pop!
      end

      state :format_spec do
        rule /'/, Str::Single, :string_single
        rule /"/, Str::Double, :string_double
        rule /\(/, Punctuation, :format_spec
        rule /\)/, Punctuation, :pop!
        rule /,/, Punctuation
        rule /[\s\n]+/, Text::Whitespace
        # Edit descriptors could be seen as a kind of "format literal".
        rule /[^\s'"(),]+/, Literal
      end
    end
  end
end
