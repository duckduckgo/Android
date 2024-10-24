require 'xcpretty/formatters/formatter'
require "xcpretty/formatters/simple"
require "fixtures/constants"

module XCPretty

    describe Simple do

      before(:each) do
        @formatter = Simple.new(false, false)
      end

      it "formats analyzing" do
        @formatter.format_analyze("CCChip8DisplayView.m", 'path/to/file').should ==
        "> Analyzing CCChip8DisplayView.m"
      end

      it "formats build target/project/configuration with target" do
        @formatter.format_build_target("The Spacer", "Pods", "Debug").should ==
        "> Building Pods/The Spacer [Debug]"
      end

      it "formats build target/project/configuration with target" do
        @formatter.format_aggregate_target("Be Aggro", "AggregateExample", "Debug").should ==
        "> Aggregate AggregateExample/Be Aggro [Debug]"
      end

      it "formats analyze target/project/configuration with target" do
        @formatter.format_analyze_target("The Spacer", "Pods", "Debug").should ==
        "> Analyzing Pods/The Spacer [Debug]"
      end

      it "formats clean target/project/configuration" do
        @formatter.format_clean_target("Pods-ObjectiveSugar", "Pods", "Debug").should ==
        "> Cleaning Pods/Pods-ObjectiveSugar [Debug]"
      end

      it 'formats compiler warnings' do
        warning = 'warning: stuff is broken'
        @formatter.format_warning(warning).should == '    ' + warning
      end

      it "formats compiling output" do
        @formatter.format_compile("NSMutableArray+ObjectiveSugar.m", 'path/to/file').should ==
        "> Compiling NSMutableArray+ObjectiveSugar.m"
      end

      it "formats compiling xib output" do
        @formatter.format_compile_xib("MainMenu.xib", 'path/to/file').should ==
        "> Compiling MainMenu.xib"
      end

      it "formats compiling storyboard output" do
        @formatter.format_compile_xib("Main.storyboard", 'path/to/file').should ==
        "> Compiling Main.storyboard"
      end

      it 'formats copying header files' do
        @formatter.format_copy_header_file('Source.h',
                                           'dir/Destination.h').should == '> Copying Source.h'
      end

      it 'formats copying plist files' do
        @formatter.format_copy_plist_file("Source.plist",
                                          'dir/Destination.plist').should == '> Copying Source.plist'
      end

      it "formats copy resource" do
        @formatter.format_cpresource("ObjectiveSugar/Default-568h@2x.png").should ==
        "> Copying ObjectiveSugar/Default-568h@2x.png"
      end

      it "formats Copy strings file" do
        @formatter.format_copy_strings_file("InfoPlist.strings").should ==
        "> Copying InfoPlist.strings"
      end

      it "formats GenerateDSYMFile" do
        @formatter.format_generate_dsym("ObjectiveSugarTests.octest.dSYM").should ==
        "> Generating 'ObjectiveSugarTests.octest.dSYM'"
      end

      it "formats info.plist processing" do
        @formatter.format_process_info_plist("The Spacer-Info.plist", "The Spacer/The Spacer-Info.plist").should ==
        "> Processing The Spacer-Info.plist"
      end

      it "formats Linking" do
        @formatter.format_linking("ObjectiveSugar", 'normal', 'i386').should ==
        "> Linking ObjectiveSugar"
      end

      it "formats Libtool" do
        @formatter.format_libtool("libPods-ObjectiveSugarTests-Kiwi.a").should ==
        "> Building library libPods-ObjectiveSugarTests-Kiwi.a"
      end

      it "formats failing tests" do
        @formatter.format_failing_test("RACCommandSpec", "enabled_signal_should_send_YES_while_executing_is_YES_and_allowsConcurrentExecution_is_YES", "expected: 1, got: 0", 'path/to/file').should ==
        "    x enabled_signal_should_send_YES_while_executing_is_YES_and_allowsConcurrentExecution_is_YES, expected: 1, got: 0"
      end

      it "formats passing tests" do
        @formatter.format_passing_test("RACCommandSpec", "_tupleByAddingObject__should_add_a_non_nil_object", "0.001").should ==
        "    . _tupleByAddingObject__should_add_a_non_nil_object (0.001 seconds)"
      end

      it "formats pending tests" do
        @formatter.format_pending_test("RACCommandSpec", "_tupleByAddingObject__should_add_a_non_nil_object").should ==
        "    P _tupleByAddingObject__should_add_a_non_nil_object [PENDING]"
      end

      it "formats measuring tests" do
        @formatter.format_measuring_test("RACCommandSpec", "_tupleByAddingObject__should_add_a_non_nil_object", "0.001").should ==
        "    T _tupleByAddingObject__should_add_a_non_nil_object measured (0.001 seconds)"
      end

      it "formats build success output" do
        @formatter.format_phase_success("BUILD").should == "> Build Succeeded"
      end

      it "formats clean success output" do
        @formatter.format_phase_success("CLEAN").should == "> Clean Succeeded"
      end

      it "formats Phase Script Execution" do
        @formatter.format_phase_script_execution("Check Pods Manifest.lock").should ==
        "> Running script 'Check Pods Manifest.lock'"
      end

      it "formats precompiling output" do
        @formatter.format_process_pch("Pods-CocoaLumberjack-prefix.pch").should ==
        "> Precompiling Pods-CocoaLumberjack-prefix.pch"
      end

      it "formats code signing" do
        @formatter.format_codesign("build/Release/CocoaChip.app").should ==
        "> Signing build/Release/CocoaChip.app"
      end

      it "formats preprocessing a file" do
        @formatter.format_preprocess("CocoaChip/CocoaChip-Info.plist").should ==
        "> Preprocessing CocoaChip/CocoaChip-Info.plist"
      end

      it "formats PBXCp" do
        @formatter.format_pbxcp("build/Release/CocoaChipCore.framework").should ==
        "> Copying build/Release/CocoaChipCore.framework"
      end

      it "formats test run start" do
        @formatter.format_test_run_started("ReactiveCocoaTests.octest(Tests)").should ==
        "Test Suite ReactiveCocoaTests.octest(Tests) started"
      end

      it "formats tests suite started" do
        @formatter.format_test_suite_started("RACKVOWrapperSpec").should ==
        "RACKVOWrapperSpec"
      end

      it "formats Touch" do
        @formatter.format_touch("/path/to/SomeFile.txt", "SomeFile.txt").should ==
        "> Touching SomeFile.txt"
      end

      it "formats TiffUtil" do
        @formatter.format_tiffutil("unbelievable.tiff").should ==
        "> Validating unbelievable.tiff"
      end

      it 'formats Check Dependencies' do
        @formatter.format_check_dependencies.should ==
          '> Check Dependencies'
      end

    end
end

