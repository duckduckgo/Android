# frozen_string_literal: true

require 'spec_helper'

describe Commander do
  include Commander::Methods

  before :each do
    $stderr = StringIO.new
    mock_terminal
    create_test_command
  end

  describe '#program' do
    it 'should set / get program information' do
      program :name, 'test'
      expect(program(:name)).to eq('test')
    end

    it 'should allow arbitrary blocks of global help documentation' do
      program :help, 'Copyright', 'TJ Holowaychuk'
      expect(program(:help)['Copyright']).to eq('TJ Holowaychuk')
    end

    it 'should raise an error when required info has not been set' do
      new_command_runner '--help'
      program :version, ''
      expect { run! }.to raise_error(Commander::Runner::CommandError)
    end

    it 'should allow aliases of help formatters' do
      program :help_formatter, :compact
      expect(program(:help_formatter)).to eq(Commander::HelpFormatter::TerminalCompact)
    end
  end

  describe '#command' do
    it 'should return a command instance when only the name is passed' do
      expect(command(:test)).to be_instance_of(Commander::Command)
    end

    it 'should return nil when the command does not exist' do
      expect(command(:im_not_real)).to be_nil
    end
  end

  describe '#separate_switches_from_description' do
    it 'should seperate switches and description returning both' do
      switches, description = *Commander::Runner.separate_switches_from_description('-h', '--help', 'display help')
      expect(switches).to eq(['-h', '--help'])
      expect(description).to eq('display help')
    end
  end

  describe '#switch_to_sym' do
    it 'should return a symbol based on the switch name' do
      expect(Commander::Runner.switch_to_sym('--trace')).to eq(:trace)
      expect(Commander::Runner.switch_to_sym('--foo-bar')).to eq(:foo_bar)
      expect(Commander::Runner.switch_to_sym('--[no-]feature"')).to eq(:feature)
      expect(Commander::Runner.switch_to_sym('--[no-]feature ARG')).to eq(:feature)
      expect(Commander::Runner.switch_to_sym('--file [ARG]')).to eq(:file)
      expect(Commander::Runner.switch_to_sym('--colors colors')).to eq(:colors)
    end
  end

  describe '#alias_command' do
    it 'should alias a command' do
      alias_command :foo, :test
      expect(command(:foo)).to eq(command(:test))
    end

    it 'should pass arguments passed to the alias when called' do
      gem_name = ''
      new_command_runner 'install', 'gem', 'commander' do
        command :install do |c|
          c.option '--gem-name NAME', 'Install a gem'
          c.when_called { |_, options| gem_name = options.gem_name }
        end
        alias_command :'install gem', :install, '--gem-name'
      end.run!
      expect(gem_name).to eq('commander')
    end
  end

  describe '#global_option' do
    it 'should be invoked when used in the args list' do
      file = ''
      new_command_runner 'test', '--config', 'foo' do
        global_option('--config FILE') { |f| file = f }
      end.run!
      expect(file).to eq('foo')
    end

    it 'should be inherited by commands' do
      quiet = nil
      new_command_runner 'foo', '--quiet' do
        global_option('--quiet', 'Suppress output')
        command :foo do |c|
          c.when_called { |_, options| quiet = options.quiet }
        end
      end.run!
      expect(quiet).to be true
    end

    it 'should be inherited by commands when provided before the command name' do
      option = nil
      new_command_runner '--global-option', 'option-value', 'command_name' do
        global_option('--global-option=GLOBAL', 'A global option')
        command :command_name do |c|
          c.when_called { |_, options| option = options.global_option }
        end
      end.run!
      expect(option).to eq('option-value')
    end

    it 'should be inherited by commands even when a block is present' do
      quiet = nil
      new_command_runner 'foo', '--quiet' do
        global_option('--quiet', 'Suppress output') {}
        command :foo do |c|
          c.when_called { |_, options| quiet = options.quiet }
        end
      end.run!
      expect(quiet).to be true
    end

    it 'should be inherited by commands when the positive form of a [no-] option' do
      quiet = nil
      new_command_runner 'foo', '--quiet' do
        global_option('--[no-]quiet', 'Suppress output') {}
        command :foo do |c|
          c.when_called { |_, options| quiet = options.quiet }
        end
      end.run!
      expect(quiet).to be true
    end

    it 'should be inherited by commands when the negative form of a [no-] option' do
      quiet = nil
      new_command_runner 'foo', '--no-quiet' do
        global_option('--[no-]quiet', 'Suppress output') {}
        command :foo do |c|
          c.when_called { |_, options| quiet = options.quiet }
        end
      end.run!
      expect(quiet).to be false
    end

    it 'should allow command arguments before the global option' do
      config = nil
      args = nil
      new_command_runner 'foo', '--config', 'config-value', 'arg1', 'arg2' do
        global_option('-c', '--config CONFIG', String)
        command :foo do |c|
          c.when_called do |arguments, options|
            options.default(config: 'default')
            args = arguments
            config = options.config
          end
        end
      end.run!
      expect(config).to eq('config-value')
      expect(args).to eq(%w(arg1 arg2))
    end

    it 'should allow command arguments after the global option' do
      config = nil
      args = nil
      new_command_runner 'foo', 'arg1', 'arg2', '--config', 'config-value' do
        global_option('-c', '--config CONFIG', String)
        command :foo do |c|
          c.when_called do |arguments, options|
            options.default(config: 'default')
            args = arguments
            config = options.config
          end
        end
      end.run!
      expect(config).to eq('config-value')
      expect(args).to eq(%w(arg1 arg2))
    end

    it 'allows global options in the form option=value' do
      config = nil
      args = nil
      new_command_runner 'test', 'arg1', '--config=config-value', 'arg2' do
        global_option('-c', '--config CONFIG', String)
        command :test do |c|
          c.when_called do |arguments, options|
            options.default(config: 'default')
            args = arguments
            config = options.config
          end
        end
      end.run!
      expect(config).to eq('config-value')
      expect(args).to eq(%w[arg1 arg2])
    end
  end

  describe '#parse_global_options' do
    it 'should parse global options before command' do
      global_option = nil
      new_command_runner('--testing-global', 'foo') do
        global_option('--testing-global') { global_option = 'MAGIC' }

        command :foo do |c|
          c.when_called {}
        end
      end.run!
      expect(global_option).to eq('MAGIC')
    end

    it 'should parse global options after command' do
      global_option = nil
      new_command_runner('foo', '--testing-global') do
        global_option('--testing-global') { global_option = 'MAGIC' }

        command :foo do |c|
          c.when_called {}
        end
      end.run!
      expect(global_option).to eq('MAGIC')
    end

    it 'should parse global options placed before command options' do
      global_option = nil
      new_command_runner('foo', '--testing-global', '--testing-command') do
        global_option('--testing-global') { global_option = 'MAGIC' }

        command :foo do |c|
          c.option('--testing-command') {}
          c.when_called {}
        end
      end.run!

      expect(global_option).to eq('MAGIC')
    end

    it 'should parse global options placed after command options' do
      global_option = nil
      new_command_runner('foo', '--testing-command', '--testing-global') do
        global_option('--testing-global') { global_option = 'MAGIC' }

        command :foo do |c|
          c.option('--testing-command') {}
          c.when_called {}
        end
      end.run!

      expect(global_option).to eq('MAGIC')
    end

    it 'should parse global options surrounded by command options' do
      global_option = nil
      new_command_runner('foo', '--testing-command', '--testing-global', '--other-command') do
        global_option('--testing-global') { global_option = 'MAGIC' }

        command :foo do |c|
          c.option('--testing-command') {}
          c.option('--other-command') {}
          c.when_called {}
        end
      end.run!

      expect(global_option).to eq('MAGIC')
    end

    it 'should not parse command options' do
      global_option = nil
      command_option = nil
      new_command_runner('foo', '--testing-command', '--testing-global') do
        global_option('--testing-global') { global_option = 'MAGIC' }

        command :foo do |c|
          c.option('--testing-command') { command_option = 'NO!' }
          c.when_called {}
        end
      end.parse_global_options

      expect(command_option).to be_nil
      expect(global_option).to eq('MAGIC')
    end

    it 'should not affect command arguments with values' do
      global_option = nil
      command_option = nil
      new_command_runner('foo', '--testing-command', 'bar', '--testing-global') do
        global_option('--testing-global') { global_option = 'MAGIC' }

        command :foo do |c|
          c.option('--testing-command VALUE') { |v| command_option = v }
          c.when_called {}
        end
      end.run!

      expect(command_option).to eq('bar')
      expect(global_option).to eq('MAGIC')
    end

    it 'should not affect global arguments with values' do
      global_option = nil
      new_command_runner('foo', '--testing-command', '--testing-global', 'bar') do
        global_option('--testing-global VALUE') { |v| global_option = v }

        command :foo do |c|
          c.option('--testing-command') {}
          c.when_called {}
        end
      end.run!

      expect(global_option).to eq('bar')
    end

    it 'should allow global arguments with values before command arguments (github issue #8)' do
      global_option = nil
      command_option = nil
      new_command_runner('foo', '--config', 'path', 'bar') do
        global_option('--config VALUE') { |v| global_option = v }

        command :foo do |c|
          c.option('bar') { command_option = 'bar' }
          c.when_called {}
        end
      end.run!

      expect(global_option).to eq('path')
      expect(command_option).to eq('bar')
    end
  end

  describe '#remove_global_options' do
    it 'should remove only specified switches' do
      options, args = [], []
      options << { switches: ['-t', '--trace'] }
      options << { switches: ['--help'] }
      options << { switches: ['--paths PATHS'] }
      args << '-t'
      args << '--help'
      args << '--command'
      args << '--command-with-arg' << 'rawr'
      args << '--paths' << '"lib/**/*.js","spec/**/*.js"'
      command_runner.remove_global_options options, args
      expect(args).to eq(['--command', '--command-with-arg', 'rawr'])
    end

    it 'should not swallow an argument unless it expects an argument' do
      options, args = [], []
      options << { switches: ['-n', '--no-arg'] }
      options << { switches: ['-y', '--yes ARG'] }
      options << { switches: ['-a', '--alternative=ARG'] }
      args << '-n' << 'alpha'
      args << '--yes' << 'deleted'
      args << '-a' << 'deleted'
      args << 'beta'
      command_runner.remove_global_options options, args
      expect(args).to eq(%w(alpha beta))
    end

    it 'should remove a switch that is the positive form of the [no-] option' do
      options, args = [], []
      options << { switches: ['-g', '--[no-]good'] }
      options << { switches: ['-y', '--yes ARG'] }
      options << { switches: ['-a', '--alternative=ARG'] }
      args << '--good' << 'alpha'
      args << '--yes' << 'deleted'
      args << '-a' << 'deleted'
      args << 'beta'
      command_runner.remove_global_options options, args
      expect(args).to eq(%w(alpha beta))
    end

    it 'should remove a switch that is the negative form of the [no-] option' do
      options, args = [], []
      options << { switches: ['-g', '--[no-]good'] }
      options << { switches: ['-y', '--yes ARG'] }
      options << { switches: ['-a', '--alternative=ARG'] }
      args << '--no-good' << 'alpha'
      args << '--yes' << 'deleted'
      args << '-a' << 'deleted'
      args << 'beta'
      command_runner.remove_global_options options, args
      expect(args).to eq(%w(alpha beta))
    end

    it 'should not remove options that start with a global option name' do
      options, args = [], []
      options << { switches: ['-v', '--version'] }
      args << '--versionCode' << 'something'
      command_runner.remove_global_options options, args
      expect(args).to eq(%w(--versionCode something))
    end

    it 'should remove specified switches value provided via equals' do
      options = [{ switches: ['--global GLOBAL'] }]
      args = ['--command', '--global=option-value', 'arg']
      command_runner.remove_global_options options, args
      expect(args).to eq(['--command', 'arg'])
    end

    it 'should not remove extra values after switches' do
      options = [{ switches: ['--global GLOBAL'] }]
      args = ['--global', '--command', 'arg']
      command_runner.remove_global_options options, args
      expect(args).to eq(['--command', 'arg'])
    end
  end

  describe '--trace' do
    it 'should display pretty errors by default' do
      expect do
        new_command_runner 'foo' do
          command(:foo) { |c| c.when_called { fail 'cookies!' } }
        end.run!
      end.to raise_error(TestSystemExit, /error: cookies!. Use --trace/)
    end

    it 'should display callstack when using this switch' do
      expect do
        new_command_runner 'foo', '--trace' do
          command(:foo) { |c| c.when_called { fail 'cookies!' } }
        end.run!
      end.to raise_error(RuntimeError)
    end
  end

  describe '#always_trace!' do
    it 'should enable tracing globally, regardless of whether --trace was passed or not' do
      expect do
        new_command_runner 'foo' do
          always_trace!
          command(:foo) { |c| c.when_called { fail 'cookies!' } }
        end.run!
      end.to raise_error(RuntimeError)
    end
  end

  describe '#never_trace!' do
    it 'should disable tracing globally, regardless of whether --trace was passed or not' do
      expect do
        new_command_runner 'help', '--trace' do
          never_trace!
        end.run!
      end.to raise_error(TestSystemExit, /invalid option: --trace/)
    end

    it 'should not prompt to use --trace switch on errors' do
      msg = nil
      begin
        new_command_runner 'foo' do
          never_trace!
          command(:foo) { |c| c.when_called { fail 'cookies!' } }
        end.run!
      rescue TestSystemExit => e
        msg = e.message
      end
      expect(msg).to match(/error: cookies!/)
      expect(msg).not_to match(/--trace/)
    end
  end

  context 'conflict between #always_trace! and #never_trace!' do
    it 'respects the last used command' do
      expect do
        new_command_runner 'foo' do
          never_trace!
          always_trace!
          command(:foo) { |c| c.when_called { fail 'cookies!' } }
        end.run!
      end.to raise_error(RuntimeError)
    end
  end

  describe '--version' do
    it 'should output program version' do
      expect(run('--version')).to eq("test 1.2.3\n")
    end
  end

  describe '--help' do
    it 'should not output an invalid command message' do
      expect(run('--help')).not_to eq("invalid command. Use --help for more information\n")
    end

    it 'can be used before or after the command and options' do
      expect(run('test', '--help')).to eq("Implement help for test here\n")
    end

    it 'can be used after the command and command arguments' do
      expect(run('test', 'command-arg', '--help')).to eq("Implement help for test here\n")
    end

    it 'can be used before a single-word command with command arguments' do
      expect(run('help', 'test', 'command-arg')).to eq("Implement help for test here\n")
    end

    it 'can be used before a multi-word command with command arguments' do
      expect(
        run('help', 'module', 'install', 'command-arg') do
          command('module install') { |c| c.when_called { say 'whee!' } }
        end
      ).to eq("Implement help for module install here\n")
    end

    describe 'help_paging program information' do
      it 'enables paging when enabled' do
        run('--help') { program :help_paging, true }
        expect(Commander::UI).to have_received(:enable_paging)
      end

      it 'is enabled by default' do
        run('--help')
        expect(Commander::UI).to have_received(:enable_paging)
      end

      it 'does not enable paging when disabled' do
        run('--help') { program :help_paging, false }
        expect(Commander::UI).not_to have_received(:enable_paging)
      end
    end
  end

  describe 'with invalid options' do
    it 'should output an invalid option message' do
      expect do
        run('test', '--invalid-option')
      end.to raise_error(TestSystemExit, /invalid option: --invalid-option/)
    end
  end

  describe 'with invalid command passed' do
    it 'should output an invalid command message' do
      expect do
        run('foo')
      end.to raise_error(TestSystemExit, /invalid command. Use --help for more information/)
    end
  end

  describe 'with invalid command passed to help' do
    it 'should output an invalid command message' do
      expect do
        run('help', 'does_not_exist')
      end.to raise_error(TestSystemExit, /invalid command. Use --help for more information/)
    end
  end

  describe 'with invalid command passed to --help' do
    it 'should output an invalid command message' do
      expect do
        run('--help', 'does_not_exist')
      end.to raise_error(TestSystemExit, /invalid command. Use --help for more information/)
    end
  end

  describe 'with invalid option passed to --help' do
    it 'should output an invalid option message' do
      expect do
        run('--help', 'test', '--invalid-option')
      end.to raise_error(TestSystemExit, /invalid option: --invalid-option/)
    end
  end

  describe '#valid_command_names_from' do
    it 'should return array of valid command names' do
      new_command_runner do
        command('foo bar') {}
        command('foo bar foo') {}
        expect(command_runner.valid_command_names_from('foo', 'bar', 'foo').sort).to eq(['foo bar', 'foo bar foo'])
      end
    end

    it 'should return empty array when no possible command names exist' do
      new_command_runner do
        expect(command_runner.valid_command_names_from('fake', 'command', 'name')).to eq([])
      end
    end

    it 'should match exact commands only' do
      new_command_runner do
        command('foo') {}
        expect(command_runner.valid_command_names_from('foobar')).to eq([])
      end
    end
  end

  describe '#command_name_from_args' do
    it 'should locate command within arbitrary arguments passed' do
      new_command_runner '--help', '--arbitrary', 'test'
      expect(command_runner.command_name_from_args).to eq('test')
    end

    it 'should locate command when provided after a global argument with value' do
      new_command_runner '--global-option', 'option-value', 'test' do
        global_option('--global-option=GLOBAL', 'A global option')
      end
      expect(command_runner.command_name_from_args).to eq('test')
    end

    it 'should support multi-word commands' do
      new_command_runner '--help', '--arbitrary', 'some', 'long', 'command', 'foo'
      command('some long command') {}
      expect(command_runner.command_name_from_args).to eq('some long command')
    end

    it 'should match the longest possible command' do
      new_command_runner '--help', '--arbitrary', 'foo', 'bar', 'foo'
      command('foo bar') {}
      command('foo bar foo') {}
      expect(command_runner.command_name_from_args).to eq('foo bar foo')
    end

    it 'should use the left-most command name when multiple are present' do
      new_command_runner 'help', 'test'
      expect(command_runner.command_name_from_args).to eq('help')
    end
  end

  describe '#active_command' do
    it 'should resolve the active command' do
      new_command_runner '--help', 'test'
      expect(command_runner.active_command).to be_instance_of(Commander::Command)
    end

    it 'should resolve active command when invalid options are passed' do
      new_command_runner '--help', 'test', '--arbitrary'
      expect(command_runner.active_command).to be_instance_of(Commander::Command)
    end

    it 'should return nil when the command is not found' do
      new_command_runner 'foo'
      expect(command_runner.active_command).to be_nil
    end
  end

  describe '#default_command' do
    it 'should allow you to default any command when one is not explicitly passed' do
      new_command_runner '--trace' do
        default_command :test
        expect(command(:test)).to receive(:run).once
        expect(command_runner.active_command).to eq(command(:test))
      end.run!
    end

    it 'should not prevent other commands from being called' do
      new_command_runner 'foo', 'bar', '--trace' do
        default_command :test
        command(:'foo bar') {}
        expect(command(:'foo bar')).to receive(:run).once
        expect(command_runner.active_command).to eq(command(:'foo bar'))
      end.run!
    end

    it 'should not prevent longer commands to use the same words as the default' do
      new_command_runner 'foo', 'bar', 'something'
      default_command :'foo bar'
      command(:'foo bar') {}
      command(:'foo bar something') {}
      expect(command_runner.active_command).to eq(command(:'foo bar something'))
    end

    it 'should allow defaulting of command aliases' do
      new_command_runner '--trace' do
        default_command :foobar
        alias_command :foobar, :test
        expect(command(:test)).to receive(:run).once
      end.run!
    end
  end

  describe 'should function correctly' do
    it 'when options are passed before the command name' do
      new_command_runner '--verbose', 'test', 'foo', 'bar' do
        @command.when_called do |args, options|
          expect(args).to eq(%w(foo bar))
          expect(options.verbose).to be true
        end
      end.run!
    end

    it 'when options are passed after the command name' do
      new_command_runner 'test', '--verbose', 'foo', 'bar' do
        @command.when_called do |args, options|
          expect(args).to eq(%w(foo bar))
          expect(options.verbose).to be true
        end
      end.run!
    end

    it 'when an argument passed is the same name as the command' do
      new_command_runner 'test', '--verbose', 'foo', 'test', 'bar' do
        @command.when_called do |args, options|
          expect(args).to eq(%w(foo test bar))
          expect(options.verbose).to be true
        end
      end.run!
    end

    it 'when using multi-word commands' do
      new_command_runner '--verbose', 'my', 'command', 'something', 'foo', 'bar' do
        command('my command') { |c| c.option('--verbose') }
        expect(command_runner.command_name_from_args).to eq('my command')
        expect(command_runner.args_without_command_name).to eq(['--verbose', 'something', 'foo', 'bar'])
      end.run!
    end

    it 'when using multi-word commands with parts of the command name as arguments' do
      new_command_runner '--verbose', 'my', 'command', 'something', 'my', 'command' do
        command('my command') { |c| c.option('--verbose') }
        expect(command_runner.command_name_from_args).to eq('my command')
        expect(command_runner.args_without_command_name).to eq(['--verbose', 'something', 'my', 'command'])
      end.run!
    end

    it 'when using multi-word commands with other commands using the same words' do
      new_command_runner '--verbose', 'my', 'command', 'something', 'my', 'command' do
        command('my command') {}
        command('my command something') { |c| c.option('--verbose') }
        expect(command_runner.command_name_from_args).to eq('my command something')
        expect(command_runner.args_without_command_name).to eq(['--verbose', 'my', 'command'])
      end.run!
    end
  end

  describe 'options with optional arguments' do
    it 'should return the argument when it is specified' do
      new_command_runner 'foo', '--optional', 'arg1' do
        command('foo') do |c|
          c.option('--optional [argument]')
          c.when_called do |_, options|
            expect(options.optional).to eq('arg1')
          end
        end
      end.run!
    end

    it 'should return true when no argument is specified for the option' do
      new_command_runner 'foo', '--optional' do
        command('foo') do |c|
          c.option('--optional [argument]')
          c.when_called do |_, options|
            expect(options.optional).to be true
          end
        end
      end.run!
    end
  end

  describe 'with double dash' do
    it 'should interpret the remainder as arguments' do
      new_command_runner 'foo', '--', '-x' do
        command('foo') do |c|
          c.option '-x', 'Switch'
          c.when_called do |args, options|
            expect(args).to eq(%w(-x))
            expect(options.x).to be_nil
          end
        end
      end.run!
    end
  end
end
