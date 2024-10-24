# frozen_string_literal: true

require 'spec_helper'

describe Commander::Command do
  include Commander::Methods

  before :each do
    mock_terminal
    create_test_command
  end

  describe 'Options' do
    before :each do
      @options = Commander::Command::Options.new
    end

    it 'should act like an open struct' do
      @options.send = 'mail'
      @options.call = true
      expect(@options.send).to eq('mail')
      expect(@options.call).to eq(true)
    end

    it 'should allow __send__ to function as always' do
      @options.send = 'foo'
      expect(@options.__send__(:send)).to eq('foo')
    end
  end

  describe '#option' do
    it 'should add options' do
      expect { @command.option '--recursive' }.to change(@command.options, :length).from(1).to(2)
    end

    it 'should allow procs as option handlers' do
      @command.option('--recursive') { |recursive| expect(recursive).to be true }
      @command.run '--recursive'
    end

    it 'should allow usage of common method names' do
      @command.option '--open file'
      @command.when_called { |_, options| expect(options.open).to eq('foo') }
      @command.run '--open', 'foo'
    end
  end

  describe '#run' do
    describe 'should invoke #when_called' do
      it 'with arguments seperated from options' do
        @command.when_called { |args, _options| expect(args.join(' ')).to eq('just some args') }
        @command.run '--verbose', 'just', 'some', 'args'
      end

      it 'calling the #call method by default when an object is called' do
        object = double 'Object'
        expect(object).to receive(:call).once
        @command.when_called object
        @command.run 'foo'
      end

      it 'should allow #action as an alias to #when_called' do
        object = double 'Object'
        expect(object).to receive(:call).once
        @command.action object
        @command.run 'foo'
      end

      it 'calling an arbitrary method when an object is called' do
        object = double 'Object'
        expect(object).to receive(:foo).once
        @command.when_called object, :foo
        @command.run 'foo'
      end

      it 'should raise an error when no handler is present' do
        expect { @command.when_called }.to raise_error(ArgumentError)
      end

      it 'should be able to be run more than once' do
        expect(@command.run('once')).to eql('test once')
        expect(@command.run('twice')).to eql('test twice')
      end

      it 'should not accumulate entries in @proxy_options when run twice' do
        expect(@command.run('--verbose')).to eql('test ')
        expect(@command.proxy_options).to eq([[:verbose, true]])
        expect(@command.run('foo')).to eql('test foo')
        expect(@command.proxy_options).to eq([])
      end
    end

    describe 'should populate options with' do
      it 'boolean values' do
        @command.option '--[no-]toggle'
        @command.when_called { |_, options| expect(options.toggle).to be true }
        @command.run '--toggle'
        @command.when_called { |_, options| expect(options.toggle).to be false }
        @command.run '--no-toggle'
      end

      it 'mandatory arguments' do
        @command.option '--file FILE'
        @command.when_called { |_, options| expect(options.file).to eq('foo') }
        @command.run '--file', 'foo'
        expect { @command.run '--file' }.to raise_error(OptionParser::MissingArgument)
      end

      describe 'optional arguments' do
        before do
          @command.option '--use-config [file] '
        end

        it 'should return the argument when provided' do
          @command.when_called { |_, options| expect(options.use_config).to eq('foo') }
          @command.run '--use-config', 'foo'
        end

        it 'should return true when present without an argument' do
          @command.when_called { |_, options| expect(options.use_config).to be true }
          @command.run '--use-config'
        end

        it 'should return nil when not present' do
          @command.when_called { |_, options| expect(options.use_config).to be_nil }
          @command.run
        end
      end

      describe 'typed arguments' do
        before do
          @command.option '--interval N', Integer
        end

        it 'should parse valid values' do
          @command.when_called { |_, options| expect(options.interval).to eq(5) }
          @command.run '--interval', '5'
        end

        it 'should reject invalid values' do
          expect { @command.run '--interval', 'invalid' }.to raise_error(OptionParser::InvalidArgument)
        end
      end

      it 'lists' do
        @command.option '--fav COLORS', Array
        @command.when_called { |_, options| expect(options.fav).to eq(%w(red green blue)) }
        @command.run '--fav', 'red,green,blue'
      end

      it 'lists with multi-word items' do
        @command.option '--fav MOVIES', Array
        @command.when_called { |_, options| expect(options.fav).to eq(['super\ bad', 'nightmare']) }
        @command.run '--fav', 'super\ bad,nightmare'
      end

      it 'defaults' do
        @command.option '--files LIST', Array
        @command.option '--interval N', Integer
        @command.when_called do |_, options|
          options.default \
            files: %w(foo bar),
            interval: 5
          expect(options.files).to eq(%w(foo bar))
          expect(options.interval).to eq(15)
        end
        @command.run '--interval', '15'
      end

      describe 'given a global option' do
        before do
          @command.global_options << [:global_option, 'gvalue']
        end

        describe 'and no command specific arguments' do
          it 'provides the global option to the command action' do
            @command.when_called { |_, options| expect(options.global_option).to eq('gvalue') }
            @command.run
          end
        end

        describe 'and a command specific option' do
          it 'provides the global option to the command action' do
            @command.when_called { |_, options| expect(options.global_option).to eq('gvalue') }
            @command.run '--verbose'
          end
        end

        describe 'and a command specific argument' do
          it 'provides the global option to the command action' do
            @command.when_called { |_, options| expect(options.global_option).to eq('gvalue') }
            @command.run 'argument'
          end
        end
      end
    end
  end
end
