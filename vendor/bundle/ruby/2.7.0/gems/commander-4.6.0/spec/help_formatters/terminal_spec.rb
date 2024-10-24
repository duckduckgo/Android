# frozen_string_literal: true

require 'spec_helper'

describe Commander::HelpFormatter::Terminal do
  include Commander::Methods

  before :each do
    mock_terminal
  end

  describe 'global help' do
    before :each do
      new_command_runner 'help' do
        command :'install gem' do |c|
          c.syntax = 'foo install gem [options]'
          c.summary = 'Install some gem'
        end
      end.run!
      @global_help = @output.string
    end

    describe 'should display' do
      it 'the command name' do
        expect(@global_help).to include('install gem')
      end

      it 'the summary' do
        expect(@global_help).to include('Install some gem')
      end
    end
  end

  describe 'command help' do
    before :each do
      new_command_runner 'help', 'install', 'gem' do
        command :'install gem' do |c|
          c.syntax = 'foo install gem [options]'
          c.summary = 'Install some gem'
          c.description = 'Install some gem, blah blah blah'
          c.example 'one', 'two'
          c.example 'three', 'four'
        end
      end.run!
      @command_help = @output.string
    end

    describe 'should display' do
      it 'the command name' do
        expect(@command_help).to include('install gem')
      end

      it 'the description' do
        expect(@command_help).to include('Install some gem, blah blah blah')
      end

      it 'all examples' do
        expect(@command_help).to include('# one')
        expect(@command_help).to include('two')
        expect(@command_help).to include('# three')
        expect(@command_help).to include('four')
      end

      it 'the syntax' do
        expect(@command_help).to include('foo install gem [options]')
      end
    end
  end
end
