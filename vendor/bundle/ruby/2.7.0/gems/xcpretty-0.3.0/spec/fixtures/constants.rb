# encoding: utf-8

KIWI = 'kiwi'
OCUNIT = 'ocunit'
SAMPLE_OCUNIT_TEST_RUN_BEGINNING = "Test Suite '/Users/musalj/Library/Developer/Xcode/DerivedData/ReactiveCocoa-eznxkbqvgfsnrvetemqloysuwagb/Build/Products/Test/ReactiveCocoaTests.octest(Tests)' started at 2013-12-10 07:04:33 +0000"
SAMPLE_KIWI_TEST_RUN_BEGINNING = "Test Suite 'ObjectiveRecordTests.xctest' started at 2013-12-10 06:15:39 +0000"
SAMPLE_SPECTA_TEST_RUN_BEGINNING = "    Test Suite 'KIFTests.xctest' started at 2014-02-28 15:43:42 +0000"
SAMPLE_OCUNIT_TEST_RUN_COMPLETION = "Test Suite '/Users/musalj/Library/Developer/Xcode/DerivedData/ReactiveCocoa-eznxkbqvgfsnrvetemqloysuwagb/Build/Products/Test/ReactiveCocoaTests.octest(Tests)' finished at 2013-12-10 07:03:03 +0000."
SAMPLE_OCUNIT_FAILED_TEST_RUN_COMPLETION = "Test Suite '/Users/dm/someplace/Macadamia.octest' failed at 2014-09-24 23:09:20 +0000."
SAMPLE_OCUNIT_PASSED_TEST_RUN_COMPLETION = "Test Suite 'Hazelnuts.xctest' passed at 2014-09-24 23:09:20 +0000."
SAMPLE_KIWI_TEST_RUN_COMPLETION = "Test Suite 'ObjectiveRecordTests.xctest' finished at 2013-12-10 06:15:42 +0000."
SAMPLE_SPECTA_TEST_RUN_COMPLETION = "     Test Suite 'KIFTests.xctest' finished at 2014-02-28 15:44:32 +0000."

SAMPLE_OCUNIT_SUITE_BEGINNING = "Test Suite 'RACKVOWrapperSpec' started at 2013-12-10 21:06:10 +0000"
SAMPLE_SPECTA_SUITE_BEGINNING = "   Test Suite 'All tests' started at 2014-02-28 19:07:41 +0000"
SAMPLE_KIWI_SUITE_COMPLETION = "Test Suite 'All tests' finished at 2013-12-08 04:26:49 +0000."
SAMPLE_OCUNIT_SUITE_COMPLETION = "Test Suite '/Users/musalj/Library/Developer/Xcode/DerivedData/ReactiveCocoa-eznxkbqvgfsnrvetemqloysuwagb/Build/Products/Test/ReactiveCocoaTests.octest(Tests)' finished at 2013-12-08 22:09:37 +0000."
SAMPLE_XCTEST_SUITE_COMPLETION = "Test Suite 'ObjectiveSugarTests.xctest' finished at 2013-12-09 04:42:13 +0000."

SAMPLE_UITEST_CASE_WITH_FAILURE = %Q(\
Test Case '-[viewUITests.vmtAboutWindow testConnectToDesktop]' started.
    t =     0.00s     Start Test at 2016-08-18 09:07:17.822
    t =     0.00s     Set Up
    t =     0.00s         Launch com.vmware.horizon
    t =     1.38s             Wait for app to idle
Wait for connect to desktop done
    t =    19.06s     Snapshot accessibility hierarchy for com.vmware.horizon
    t =    20.31s     Find: Descendants matching type Window
    t =    20.32s     Find: Elements matching predicate '"rdsh1" IN identifiers'
Connect to desktop done
    t =    20.32s     Click "Disconnect" Button
    t =    20.32s         Wait for app to idle
    t =    20.39s         Find the "Disconnect" Button
    t =    20.39s             Snapshot accessibility hierarchy for com.vmware.horizon
    t =    20.53s             Find: Descendants matching type Window
    t =    20.53s             Find: Elements matching predicate '"rdsh1" IN identifiers'
    t =    21.54s             Find the "Disconnect" Button (retry 1)
    t =    21.54s                 Snapshot accessibility hierarchy for com.vmware.horizon
    t =    21.80s                 Find: Descendants matching type Window
    t =    21.80s                 Find: Elements matching predicate '"rdsh1" IN identifiers'
    t =    21.81s                 Find: Descendants matching type Toolbar
    t =    21.81s                 Find: Descendants matching type Button
    t =    21.81s                 Find: Elements matching predicate '"Disconnect" IN identifiers'
    t =    21.81s         Synthesize event
    t =    22.27s             Assertion Failure: <unknown>:0: UI Testing Failure - Unable to find hit point for element Button 0x608001165880: {{74.0, -54.0}, {44.0, 38.0}}, label: 'Disconnect'
    t =    22.29s     Tear Down
Test Case '-[viewUITests.vmtAboutWindow testConnectToDesktop]' failed (22.490 seconds).
)
SAMPLE_XCTEST_FAILURE = ""
SAMPLE_KIWI_FAILURE = "/Users/musalj/code/OSS/ObjectiveSugar/Example/ObjectiveSugarTests/NSNumberTests.m:49: error: -[NumberAdditions Iterators_TimesIteratesTheExactNumberOfTimes] : 'Iterators, times： iterates the exact number of times' [FAILED], expected subject to equal 4, got 5"
SAMPLE_OLD_SPECTA_FAILURE = "/Users/musalj/code/OSS/ReactiveCocoa/ReactiveCocoaFramework/ReactiveCocoaTests/RACCommandSpec.m:458: error: -[RACCommandSpec enabled_signal_should_send_YES_while_executing_is_YES_and_allowsConcurrentExecution_is_YES] : expected: 1, got: 0"
SAMPLE_SPECTA_FAILURE = "         Test Case '-[SKWelcomeViewControllerSpecSpec SKWelcomeViewController_When_a_user_opens_the_app_from_a_clean_installation_displays_the_welcome_screen]' started. \n/Users/vickeryj/Code/ipad-register/KIFTests/Specs/SKWelcomeViewControllerSpec.m:11: error: -[SKWelcomeViewControllerSpecSpec SKWelcomeViewController_When_a_user_opens_the_app_from_a_clean_installation_displays_the_welcome_screen] : The step timed out after 2.00 seconds: Failed to find accessibility element with the label \"The asimplest way to make smarter business decisions\""
SAMPLE_SWIFT_FATAL_ERROR_IN_TEST_MAKE_TESTS_RESTARTING = %Q(\
Test Suite 'All tests' started at 2017-07-10 15:01:12.049
Test Suite 'xcprettyFatalErrorTests.xctest' started at 2017-07-10 15:01:12.049
Test Suite 'xcprettyFatalErrorTests' started at 2017-07-10 15:01:12.049
Test Case '-[xcprettyFatalErrorTests.xcprettyFatalErrorTests testExample]' started.
Test Case '-[xcprettyFatalErrorTests.xcprettyFatalErrorTests testExample]' passed (0.001 seconds).
Test Case '-[xcprettyFatalErrorTests.xcprettyFatalErrorTests testFatalError]' started.
fatal error: unexpectedly found nil while unwrapping an Optional value

Restarting after unexpected exit or crash in xcprettyFatalErrorTests/testFatalError(); summary will include totals from previous launches.

Test Suite 'Selected tests' started at 2017-07-10 15:01:17.698
Test Suite 'xcprettyFatalErrorTests.xctest' started at 2017-07-10 15:01:17.698
Test Suite 'xcprettyFatalErrorTests' started at 2017-07-10 15:01:17.699
Test Case '-[xcprettyFatalErrorTests.xcprettyFatalErrorTests testPerformanceExample]' started.
/Users/kenji/Desktop/xcprettyFatalError/xcprettyFatalErrorTests/xcprettyFatalErrorTests.swift:40: Test Case '-[xcprettyFatalErrorTests.xcprettyFatalErrorTests testPerformanceExample]' measured [Time, seconds] average: 0.000, relative standard deviation: 174.575%, values: [0.000005, 0.000001, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000], performanceMetricID:com.apple.XCTPerformanceMetric_WallClockTime, baselineName: "", baselineAverage: , maxPercentRegression: 10.000%, maxPercentRelativeStandardDeviation: 10.000%, maxRegression: 0.100, maxStandardDeviation: 0.100
Test Case '-[xcprettyFatalErrorTests.xcprettyFatalErrorTests testPerformanceExample]' passed (0.258 seconds).
Test Suite 'xcprettyFatalErrorTests' failed at 2017-07-10 15:01:17.957.
     Executed 3 tests, with 1 failure (0 unexpected) in 0.258 (0.259) seconds
Test Suite 'xcprettyFatalErrorTests.xctest' failed at 2017-07-10 15:01:17.958.
     Executed 3 tests, with 1 failure (0 unexpected) in 0.258 (0.259) seconds
Test Suite 'Selected tests' failed at 2017-07-10 15:01:17.958.
     Executed 3 tests, with 1 failure (0 unexpected) in 0.258 (0.260) seconds
)

SAMPLE_BUILD = "=== BUILD TARGET The Spacer OF PROJECT Pods WITH THE DEFAULT CONFIGURATION Debug ==="
SAMPLE_ANALYZE_TARGET = "=== ANALYZE TARGET The Spacer OF PROJECT Pods WITH THE DEFAULT CONFIGURATION Debug ==="
SAMPLE_AGGREGATE_TARGET = "=== BUILD AGGREGATE TARGET Be Aggro OF PROJECT AggregateExample WITH CONFIGURATION Debug ==="
SAMPLE_CLEAN = "=== CLEAN TARGET Pods-ObjectiveSugar OF PROJECT Pods WITH CONFIGURATION Debug ==="
SAMPLE_ANOTHER_CLEAN = "=== CLEAN TARGET Pods OF PROJECT Pods WITH CONFIGURATION Debug ==="
SAMPLE_BUILD_SUCCEEDED = "** BUILD SUCCEEDED **"
SAMPLE_CLEAN_SUCCEEDED = "** CLEAN SUCCEEDED **"
SAMPLE_CLEAN_REMOVE = %Q(
Clean.Remove clean /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugarTests.build
    builtin-rm -rf /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugarTests.build
)
SAMPLE_EXECUTED_TESTS = "Executed 4 tests, with 0 failures (0 unexpected) in 0.003 (0.004) seconds"
SAMPLE_SPECTA_EXECUTED_TESTS = "       Executed 4 tests, with 0 failures (0 unexpected) in 10.192 (10.193) seconds"
SAMPLE_OCUNIT_TEST = "Test Case '-[RACCommandSpec enabled_signal_should_send_YES_while_executing_is_YES]' passed (0.001 seconds)."
SAMPLE_SPECTA_TEST = "         Test Case '-[SKWelcomeActivationViewControllerSpecSpec SKWelcomeActivationViewController_When_a_user_enters_their_details_lets_them_enter_a_valid_manager_code]' passed (0.725 seconds)."
SAMPLE_SLOWISH_TEST = "Test Case '-[RACCommandSpec enabled_signal_should_send_YES_while_executing_is_YES_and_allowsConcurrentExecution_is_YES]' passed (0.026 seconds)."
SAMPLE_SLOW_TEST = "Test Case '-[RACCommandSpec enabled_signal_should_send_YES_while_executing_is_YES_and_allowsConcurrentExecution_is_YES]' passed (0.101 seconds)."
SAMPLE_KIWI_TEST = "Test Case '-[MappingsTests Mappings_SupportsCreatingAParentObjectUsingJustIDFromTheServer]' passed (0.004 seconds)."
SAMPLE_PENDING_KIWI_TEST = "Test Case '-[TAPIConversationSpec TAPIConversation_createConversation_SendsAPOSTRequestToTheConversationsEndpointPENDING]' passed (0.001 seconds)."
SAMPLE_MEASURING_TEST = "<unknown>:0: Test Case '-[SecEncodeTransformTests.SecEncodeTransformTests test_RFC4648_Decode_UsingBase32]' measured [Time, seconds] average: 0.013, relative standard deviation: 26.773%, values: [0.023838, 0.012034, 0.013512, 0.011022, 0.011203, 0.012814, 0.011131, 0.012740, 0.013646, 0.012145], performanceMetricID:com.apple.XCTPerformanceMetric_WallClockTime, baselineName: "", baselineAverage: , maxPercentRegression: 10.000%, maxPercentRelativeStandardDeviation: 10.000%, maxRegression: 0.100, maxStandardDeviation: 0.100"
SAMPLE_COMPILE = %Q(
CompileC /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-ObjectiveSugar.build/Objects-normal/i386/NSMutableArray+ObjectiveSugar.o /Users/musalj/code/OSS/ObjectiveSugar/Classes/NSMutableArray+ObjectiveSugar.m normal i386 objective-c com.apple.compilers.llvm.clang.1_0.compiler
    cd /Users/musalj/code/OSS/ObjectiveSugar/Example/Pods
    setenv LANG en_US.US-ASCII
    setenv PATH "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/Users/musalj/code/go/bin:/Users/musalj/.rbenv/shims:/Users/musalj/.rbenv/bin:/usr/local/share/npm/bin:/usr/local/bin:/Library/Python/2.7/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -x objective-c -arch i386 -fmessage-length=178 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit=0 -fcolor-diagnostics -std=gnu99 -fmodules -fmodules-cache-path=/Users/musalj/Library/Developer/Xcode/DerivedData/ModuleCache -Wno-trigraphs -fpascal-strings -O0 -Wno-missing-field-initializers -Wno-missing-prototypes -Werror=return-type -Wno-implicit-atomic-properties -Werror=deprecated-objc-isa-usage -Werror=objc-root-class -Wno-receiver-is-weak -Wno-arc-repeated-use-of-weak -Wno-missing-braces -Wparentheses -Wswitch -Wunused-function -Wno-unused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wno-unknown-pragmas -Wno-shadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wbool-conversion -Wenum-conversion -Wshorten-64-to-32 -Wpointer-sign -Wno-newline-eof -Wno-selector -Wno-strict-selector-match -Wundeclared-selector -Wno-deprecated-implementations -DDEBUG=1 -DCOCOAPODS=1 -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator7.0.sdk -fexceptions -fasm-blocks -fstrict-aliasing -Wprotocol -Wdeprecated-declarations -g -Wno-sign-conversion -fobjc-abi-version=2 -fobjc-legacy-dispatch -mios-simulator-version-min=5.0 -iquote /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-ObjectiveSugar.build/Pods-ObjectiveSugar-generated-files.hmap -I/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-ObjectiveSugar.build/Pods-ObjectiveSugar-own-target-headers.hmap -I/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-ObjectiveSugar.build/Pods-ObjectiveSugar-all-target-headers.hmap -iquote /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-ObjectiveSugar.build/Pods-ObjectiveSugar-project-headers.hmap -I/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/include -I/Users/musalj/code/OSS/ObjectiveSugar/Example/Pods/BuildHeaders -I/Users/musalj/code/OSS/ObjectiveSugar/Example/Pods/BuildHeaders/ObjectiveSugar -I/Users/musalj/code/OSS/ObjectiveSugar/Example/Pods/Headers -I/Users/musalj/code/OSS/ObjectiveSugar/Example/Pods/Headers/Kiwi -I/Users/musalj/code/OSS/ObjectiveSugar/Example/Pods/Headers/ObjectiveSugar -I/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-ObjectiveSugar.build/DerivedSources/i386 -I/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-ObjectiveSugar.build/DerivedSources -F/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator -include /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/PrecompiledHeaders/Pods-ObjectiveSugar-prefix-fbehxvikzshezadcwuseekuhbnus/Pods-ObjectiveSugar-prefix.pch -MMD -MT dependencies -MF /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-ObjectiveSugar.build/Objects-normal/i386/NSMutableArray+ObjectiveSugar.d --serialize-diagnostics /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-ObjectiveSugar.build/Objects-normal/i386/NSMutableArray+ObjectiveSugar.dia -c /Users/musalj/code/OSS/ObjectiveSugar/Classes/NSMutableArray+ObjectiveSugar.m -o /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-ObjectiveSugar.build/Objects-normal/i386/NSMutableArray+ObjectiveSugar.o
)
SAMPLE_COMPILE_SPACE_IN_PATH = %Q(
CompileC /Users/supermarin/Library/Developer/Xcode/DerivedData/SampleWorkspace-furbnwegumclnwbotvisbynpuqvh/Build/Intermediates/SACore.build/Debug-iphonesimulator/SACore.build/Objects-normal/i386/SASellableItem.o SACore/App/Models/Core\\ Data/human/SASellableItem.m normal i386 objective-c com.apple.compilers.llvm.clang.1_0.compiler
    cd /Users/supermarin/Documents/source/ios-sample-newgen/libs/SACore
    setenv LANG en_US.US-ASCII
    setenv PATH "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin:/Users/supermarin/.rvm/bin"
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -x objective-c -arch i386 -fmessage-length=204 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit=0 -fcolor-diagnostics -std=gnu99 -fobjc-arc -Wno-trigraphs -fpascal-strings -O0 -Werror -Wno-missing-field-initializers -Wno-missing-prototypes -Wno-implicit-atomic-properties -Wno-receiver-is-weak -Wno-arc-repeated-use-of-weak -Wduplicate-method-match -Wno-missing-braces -Wparentheses -Wswitch -Wno-unused-function -Wno-unused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wno-unknown-pragmas -Wno-shadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wno-bool-conversion -Wenum-conversion -Wno-shorten-64-to-32 -Wpointer-sign -Wno-newline-eof -Wno-selector -Wno-strict-selector-match -Wno-undeclared-selector -Wno-deprecated-implementations -DDEBUG=1 -DDEBUG=1 -DBUILD_USER=@\"supermarin\" -DNI_DYNAMIC_VIEWS -DALLOW_ENV_SWITCH -DSA_CORE_BUNDLE_NAME=@\"SACore.bundle\" -DNI_DEBUG_CSS_SELECTOR_TARGET=setAccessibilityValue -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator7.0.sdk -fexceptions -fasm-blocks -fstrict-aliasing -fprofile-arcs -ftest-coverage -Wprotocol -Wdeprecated-declarations -g -Wno-sign-conversion -fobjc-abi-version=2 -fobjc-legacy-dispatch -mios-simulator-version-min=5.0 -iquote /Users/supermarin/Library/Developer/Xcode/DerivedData/SampleWorkspace-furbnwegumclnwbotvisbynpuqvh/Build/Intermediates/SACore.build/Debug-iphonesimulator/SACore.build/SACore-generated-files.hmap -I/Users/supermarin/Library/Developer/Xcode/DerivedData/SampleWorkspace-furbnwegumclnwbotvisbynpuqvh/Build/Intermediates/SACore.build/Debug-iphonesimulator/SACore.build/SACore-own-target-headers.hmap -I/Users/supermarin/Library/Developer/Xcode/DerivedData/SampleWorkspace-furbnwegumclnwbotvisbynpuqvh/Build/Intermediates/SACore.build/Debug-iphonesimulator/SACore.build/SACore-all-target-headers.hmap -iquote /Users/supermarin/Library/Developer/Xcode/DerivedData/SampleWorkspace-furbnwegumclnwbotvisbynpuqvh/Build/Intermediates/SACore.build/Debug-iphonesimulator/SACore.build/SACore-project-headers.hmap -I/Users/supermarin/Library/Developer/Xcode/DerivedData/SampleWorkspace-furbnwegumclnwbotvisbynpuqvh/Build/Products/Debug-iphonesimulator/include -I/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator7.0.sdk/usr/include/libxml2 -I../../contrib -I../../contrib/CardIO -I../../contrib/Crittercism -I../../contrib/Epson -I../../contrib/GCDAsyncSocket -I../../contrib/iOS_fpti_sdk -I../../contrib/JASidePanels -I../../contrib/JSONKit -I../../contrib/LibComponentLogging-Core -I../../contrib/LibComponentLogging-NSLogger -I../../contrib/MagicalRecord -I../../contrib/MongooseDaemon -I../../contrib/nimbus -I../../contrib/NSLogger -I../../contrib/objective-c-formatter -I../../contrib/SARiskComponent -I../../contrib/Star -I../../contrib/CardIO/CardIO -I../../contrib/Crittercism/Crittercism_v3_5_1 -I../../contrib/iOS_fpti_sdk/fpti_sample -I../../contrib/iOS_fpti_sdk/ios_fpti_sdk -I../../contrib/JASidePanels/JASidePanels -I../../contrib/MagicalRecord/Docs -I../../contrib/MagicalRecord/MagicalRecord -I../../contrib/MagicalRecord/Project\ Files -I../../contrib/MagicalRecord/Samples -I../../contrib/nimbus/docs -I../../contrib/nimbus/examples -I../../contrib/nimbus/scripts -I../../contrib/nimbus/src -I../../contrib/nimbus/templates -I../../contrib/nimbus/thirdparty -I../../contrib/NSLogger/Client\ Logger -I../../contrib/NSLogger/Desktop\ Viewer -I../../contrib/NSLogger/Docs -I../../contrib/NSLogger/iPad\ Viewer -I../../contrib/NSLogger/Screenshots -I../../contrib/SARiskComponent/SARiskComponent -I../../contrib/SARiskComponent/SARiskComponentTests -I../../contrib/Star/Distributables -I../../contrib/Star/Documents -I../../contrib/Star/Samples -I../../contrib/Crittercism/Crittercism_v3_5_1/CrittercismExampleApp -I../../contrib/Crittercism/Crittercism_v3_5_1/CrittercismSDK -I../../contrib/Crittercism/Crittercism_v3_5_1/Docs -I../../contrib/iOS_fpti_sdk/fpti_sample/fpti_sample -I../../contrib/iOS_fpti_sdk/ios_fpti_sdk/ios_fpti_sdk -I../../contrib/iOS_fpti_sdk/ios_fpti_sdk/ios_fpti_sdkTests -I../../contrib/JASidePanels/JASidePanels/Demo -I../../contrib/JASidePanels/JASidePanels/Source -I../../contrib/MagicalRecord/MagicalRecord/Categories -I../../contrib/MagicalRecord/MagicalRecord/Core -I../../contrib/MagicalRecord/Project\ Files/Library -I../../contrib/MagicalRecord/Project\ Files/Tests -I../../contrib/MagicalRecord/Samples/iOS -I../../contrib/MagicalRecord/Samples/Mac -I../../contrib/nimbus/docs/gfx -I../../contrib/nimbus/docs/js -I../../contrib/nimbus/docs/keynotes -I../../contrib/nimbus/examples/catalog -I../../contrib/nimbus/examples/css -I../../contrib/nimbus/examples/photos -I../../contrib/nimbus/scripts/ios -I../../contrib/nimbus/src/attributedlabel -I../../contrib/nimbus/src/badge -I../../contrib/nimbus/src/collections -I../../contrib/nimbus/src/core -I../../contrib/nimbus/src/css -I../../contrib/nimbus/src/interapp -I../../contrib/nimbus/src/launcher -I../../contrib/nimbus/src/models -I../../contrib/nimbus/src/networkcontrollers -I../../contrib/nimbus/src/networkimage -I../../contrib/nimbus/src/overview -I../../contrib/nimbus/src/pagingscrollview -I../../contrib/nimbus/src/photos -I../../contrib/nimbus/src/resources -I../../contrib/nimbus/src/textfield -I../../contrib/nimbus/src/webcontroller -I../../contrib/nimbus/templates/xcode3 -I../../contrib/nimbus/templates/xcode4 -I../../contrib/nimbus/thirdparty/AFNetworking -I../../contrib/nimbus/thirdparty/JSONKit -I../../contrib/NSLogger/Client\ Logger/Android -I../../contrib/NSLogger/Client\ Logger/iOS -I../../contrib/NSLogger/Desktop\ Viewer/Classes -I../../contrib/NSLogger/Desktop\ Viewer/Resources -I../../contrib/NSLogger/Desktop\ Viewer/Third\ Party -I../../contrib/NSLogger/iPad\ Viewer/Constants -I../../contrib/NSLogger/iPad\ Viewer/CoreDataModel -I../../contrib/NSLogger/iPad\ Viewer/DataStorage -I../../contrib/NSLogger/iPad\ Viewer/iPad\ Viewer -I../../contrib/NSLogger/iPad\ Viewer/Managers -I../../contrib/NSLogger/iPad\ Viewer/Model -I../../contrib/NSLogger/iPad\ Viewer/NSLoggerResource.bundle -I../../contrib/NSLogger/iPad\ Viewer/Utilities -I../../contrib/NSLogger/iPad\ Viewer/Vendors -I../../contrib/NSLogger/iPad\ Viewer/ViewController -I../../contrib/NSLogger/iPad\ Viewer/Views\ &\ Cells -I../../contrib/Star/Samples/IOS_SDK -I../../contrib/Star/Samples/Resources-iPad -I../../contrib/Crittercism/Crittercism_v3_5_1/CrittercismExampleApp/CrittercismExampleApp -I../../contrib/MagicalRecord/MagicalRecord/Categories/DataImport -I../../contrib/MagicalRecord/MagicalRecord/Categories/NSManagedObject -I../../contrib/MagicalRecord/MagicalRecord/Categories/NSManagedObjectContext -I../../contrib/MagicalRecord/Project\ Files/Library/Support -I../../contrib/MagicalRecord/Project\ Files/Tests/Core -I../../contrib/MagicalRecord/Project\ Files/Tests/DataImport -I../../contrib/MagicalRecord/Project\ Files/Tests/Fixtures -I../../contrib/MagicalRecord/Project\ Files/Tests/Support -I../../contrib/MagicalRecord/Samples/iOS/Application -I../../contrib/MagicalRecord/Samples/iOS/Resources -I../../contrib/nimbus/examples/catalog/Catalog -I../../contrib/nimbus/examples/css/CSSDemo -I../../contrib/nimbus/examples/photos/NetworkPhotoAlbums -I../../contrib/nimbus/src/attributedlabel/src -I../../contrib/nimbus/src/attributedlabel/unittests -I../../contrib/nimbus/src/badge/src -I../../contrib/nimbus/src/badge/unittests -I../../contrib/nimbus/src/collections/src -I../../contrib/nimbus/src/core/examples -I../../contrib/nimbus/src/core/src -I../../contrib/nimbus/src/core/unittests -I../../contrib/nimbus/src/css/chameleon -I../../contrib/nimbus/src/css/grammar -I../../contrib/nimbus/src/css/src -I../../contrib/nimbus/src/css/unittests -I../../contrib/nimbus/src/interapp/src -I../../contrib/nimbus/src/interapp/unittests -I../../contrib/nimbus/src/launcher/src -I../../contrib/nimbus/src/launcher/unittests -I../../contrib/nimbus/src/models/examples -I../../contrib/nimbus/src/models/src -I../../contrib/nimbus/src/models/unittests -I../../contrib/nimbus/src/networkcontrollers/src -I../../contrib/nimbus/src/networkcontrollers/unittests -I../../contrib/nimbus/src/networkimage/src -I../../contrib/nimbus/src/networkimage/unittests -I../../contrib/nimbus/src/overview/resources -I../../contrib/nimbus/src/overview/src -I../../contrib/nimbus/src/overview/unittests -I../../contrib/nimbus/src/pagingscrollview/src -I../../contrib/nimbus/src/pagingscrollview/unittests -I../../contrib/nimbus/src/photos/resources -I../../contrib/nimbus/src/photos/src -I../../contrib/nimbus/src/photos/unittests -I../../contrib/nimbus/src/textfield/src -I../../contrib/nimbus/src/webcontroller/resources -I../../contrib/nimbus/src/webcontroller/src -I../../contrib/nimbus/src/webcontroller/unittests -I../../contrib/nimbus/templates/xcode3/projects -I../../contrib/nimbus/templates/xcode3/source -I../../contrib/nimbus/templates/xcode4/projects -I../../contrib/nimbus/thirdparty/AFNetworking/AFNetworking -I../../contrib/nimbus/thirdparty/AFNetworking/AFNetworking.xcworkspace -I../../contrib/nimbus/thirdparty/AFNetworking/Example -I../../contrib/NSLogger/Client\ Logger/Android/client-code -I../../contrib/NSLogger/Client\ Logger/Android/example -I../../contrib/NSLogger/Client\ Logger/iOS/iPhone\ Test\ App -I../../contrib/NSLogger/Client\ Logger/iOS/iPhone\ Test\ App\ (ARC) -I../../contrib/NSLogger/Client\ Logger/iOS/Mac\ Test\ App -I../../contrib/NSLogger/Desktop\ Viewer/Resources/Icon -I../../contrib/NSLogger/Desktop\ Viewer/Third\ Party/BWToolkit -I../../contrib/NSLogger/Desktop\ Viewer/Third\ Party/HockeySDK-Mac -I../../contrib/NSLogger/Desktop\ Viewer/Third\ Party/SSSelectableToolbar -I../../contrib/NSLogger/iPad\ Viewer/CoreDataModel/LoggerMessages.xcdatamodeld -I../../contrib/NSLogger/iPad\ Viewer/DataStorage/DataOperation -I../../contrib/NSLogger/iPad\ Viewer/Model/MessageFormatter -I../../contrib/NSLogger/iPad\ Viewer/Model/MessageSizeCalculator -I../../contrib/NSLogger/iPad\ Viewer/Model/NativeLogFormat -I../../contrib/NSLogger/iPad\ Viewer/NSLoggerResource.bundle/background -I../../contrib/NSLogger/iPad\ Viewer/NSLoggerResource.bundle/button -I../../contrib/NSLogger/iPad\ Viewer/NSLoggerResource.bundle/Certificate -I../../contrib/NSLogger/iPad\ Viewer/NSLoggerResource.bundle/fonts -I../../contrib/NSLogger/iPad\ Viewer/NSLoggerResource.bundle/Icon -I../../contrib/NSLogger/iPad\ Viewer/NSLoggerResource.bundle/Profiles -I../../contrib/NSLogger/iPad\ Viewer/Utilities/CoreData -I../../contrib/NSLogger/iPad\ Viewer/Utilities/Directory -I../../contrib/NSLogger/iPad\ Viewer/Utilities/ICloud -I../../contrib/NSLogger/iPad\ Viewer/Utilities/Macro -I../../contrib/NSLogger/iPad\ Viewer/Utilities/String -I../../contrib/NSLogger/iPad\ Viewer/Utilities/UIColor -I../../contrib/NSLogger/iPad\ Viewer/Vendors/KGNoise -I../../contrib/NSLogger/iPad\ Viewer/ViewController/BaseViewController -I../../contrib/NSLogger/iPad\ Viewer/ViewController/MessageViewController -I../../contrib/NSLogger/iPad\ Viewer/Views\ &\ Cells/MessageCell -I../../contrib/Star/Samples/IOS_SDK/Resources -I../../contrib/Star/Samples/Resources-iPad/IOS_SDK -I../../contrib/MagicalRecord/Project\ Files/Tests/Fixtures/TestModel -I../../contrib/MagicalRecord/Project\ Files/Tests/Fixtures/TestModel.xcdatamodeld -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor -I../../contrib/MagicalRecord/Samples/iOS/Application/Categories -I../../contrib/MagicalRecord/Samples/iOS/Application/Controllers -I../../contrib/MagicalRecord/Samples/iOS/Application/Delegate -I../../contrib/MagicalRecord/Samples/iOS/Application/Models -I../../contrib/MagicalRecord/Samples/iOS/Application/Support -I../../contrib/MagicalRecord/Samples/iOS/Application/Views -I../../contrib/MagicalRecord/Samples/iOS/Resources/images -I../../contrib/MagicalRecord/Samples/iOS/Resources/RecipeData -I../../contrib/nimbus/examples/css/CSSDemo/resources -I../../contrib/nimbus/examples/photos/NetworkPhotoAlbums/resources -I../../contrib/nimbus/examples/photos/NetworkPhotoAlbums/src -I../../contrib/nimbus/src/overview/resources/NimbusOverviewer.bundle -I../../contrib/nimbus/src/photos/resources/NimbusPhotos.bundle -I../../contrib/nimbus/src/webcontroller/resources/NimbusWebController.bundle -I../../contrib/nimbus/templates/xcode3/projects/Skeleton\ App -I../../contrib/nimbus/templates/xcode3/source/Nimbus\ Hacking -I../../contrib/nimbus/templates/xcode4/projects/Base.xctemplate -I../../contrib/nimbus/templates/xcode4/projects/Bundle\ Base.xctemplate -I../../contrib/nimbus/templates/xcode4/projects/Cocoa\ Touch\ Application.xctemplate -I../../contrib/nimbus/templates/xcode4/projects/iPhone\ Base.xctemplate -I../../contrib/nimbus/templates/xcode4/projects/Objective-C\ Application.xctemplate -I../../contrib/nimbus/templates/xcode4/projects/Skeleton\ App\ (Apache\ 2.0\ Licensed).xctemplate -I../../contrib/nimbus/thirdparty/AFNetworking/Example/Classes -I../../contrib/nimbus/thirdparty/AFNetworking/Example/Images -I../../contrib/NSLogger/Client\ Logger/Android/client-code/lib -I../../contrib/NSLogger/Client\ Logger/Android/client-code/src -I../../contrib/NSLogger/Client\ Logger/Android/example/com -I../../contrib/NSLogger/Client\ Logger/iOS/iPhone\ Test\ App/Classes -I../../contrib/NSLogger/Client\ Logger/iOS/iPhone\ Test\ App\ (ARC)/Classes -I../../contrib/NSLogger/Desktop\ Viewer/Third\ Party/HockeySDK-Mac/CrashReporter.framework.dSYM -I../../contrib/NSLogger/Desktop\ Viewer/Third\ Party/HockeySDK-Mac/HockeySDK.framework.dSYM -I../../contrib/NSLogger/iPad\ Viewer/CoreDataModel/LoggerMessages.xcdatamodeld/LoggerMessages.xcdatamodel -I../../contrib/NSLogger/iPad\ Viewer/ViewController/BaseViewController/KeyboardAddition -I../../contrib/MagicalRecord/Project\ Files/Tests/Fixtures/TestModel.xcdatamodeld/TestModel.xcdatamodel -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Expecta -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta -I../../contrib/MagicalRecord/Samples/iOS/Application/Models/entities -I../../contrib/MagicalRecord/Samples/iOS/Application/Models/generated -I../../contrib/MagicalRecord/Samples/iOS/Application/Models/Recipes.xcdatamodeld -I../../contrib/nimbus/examples/css/CSSDemo/resources/css -I../../contrib/nimbus/src/overview/resources/NimbusOverviewer.bundle/gfx -I../../contrib/nimbus/src/photos/resources/NimbusPhotos.bundle/gfx -I../../contrib/nimbus/src/webcontroller/resources/NimbusWebController.bundle/gfx -I../../contrib/nimbus/templates/xcode3/projects/Skeleton\ App/Skeleton\ App\ (Apache\ 2.0\ Licensed) -I../../contrib/nimbus/templates/xcode3/source/Nimbus\ Hacking/Objective-C\ Class -I../../contrib/nimbus/templates/xcode4/projects/Skeleton\ App\ (Apache\ 2.0\ Licensed).xctemplate/resources -I../../contrib/nimbus/templates/xcode4/projects/Skeleton\ App\ (Apache\ 2.0\ Licensed).xctemplate/src -I../../contrib/nimbus/thirdparty/AFNetworking/Example/Classes/Controllers -I../../contrib/nimbus/thirdparty/AFNetworking/Example/Classes/Models -I../../contrib/nimbus/thirdparty/AFNetworking/Example/Classes/Views -I../../contrib/NSLogger/Client\ Logger/Android/client-code/src/com -I../../contrib/NSLogger/Client\ Logger/Android/example/com/example -I../../contrib/NSLogger/Desktop\ Viewer/Third\ Party/HockeySDK-Mac/CrashReporter.framework.dSYM/Contents -I../../contrib/NSLogger/Desktop\ Viewer/Third\ Party/HockeySDK-Mac/HockeySDK.framework.dSYM/Contents -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Expecta/products -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Expecta/src -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Expecta/test -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta/products -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta/src -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta/templates -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta/test -I../../contrib/MagicalRecord/Samples/iOS/Application/Models/Recipes.xcdatamodeld/Recipes\ 2.xcdatamodel -I../../contrib/MagicalRecord/Samples/iOS/Application/Models/Recipes.xcdatamodeld/Recipes.xcdatamodel -I../../contrib/nimbus/examples/css/CSSDemo/resources/css/root -I../../contrib/nimbus/templates/xcode3/projects/Skeleton\ App/Skeleton\ App\ (Apache\ 2.0\ Licensed)/resources -I../../contrib/nimbus/templates/xcode3/projects/Skeleton\ App/Skeleton\ App\ (Apache\ 2.0\ Licensed)/src -I../../contrib/nimbus/templates/xcode3/source/Nimbus\ Hacking/Objective-C\ Class/NSObject\ subclass.pbfiletemplate -I../../contrib/nimbus/templates/xcode3/source/Nimbus\ Hacking/Objective-C\ Class/UIView\ subclass.pbfiletemplate -I../../contrib/nimbus/templates/xcode3/source/Nimbus\ Hacking/Objective-C\ Class/UIViewController\ subclass.pbfiletemplate -I../../contrib/NSLogger/Client\ Logger/Android/client-code/src/com/NSLogger -I../../contrib/NSLogger/Desktop\ Viewer/Third\ Party/HockeySDK-Mac/CrashReporter.framework.dSYM/Contents/Resources -I../../contrib/NSLogger/Desktop\ Viewer/Third\ Party/HockeySDK-Mac/HockeySDK.framework.dSYM/Contents/Resources -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Expecta/src/matchers -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Expecta/test/helpers -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Expecta/test/matchers -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Expecta/test/support -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta/templates/Specta -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta/test/helpers -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta/test/support -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta/test/vendor -I../../contrib/NSLogger/Desktop\ Viewer/Third\ Party/HockeySDK-Mac/CrashReporter.framework.dSYM/Contents/Resources/DWARF -I../../contrib/NSLogger/Desktop\ Viewer/Third\ Party/HockeySDK-Mac/HockeySDK.framework.dSYM/Contents/Resources/DWARF -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta/templates/Specta/Specta\ Spec\ Helper.xctemplate -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta/templates/Specta/Specta\ Spec.xctemplate -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta/test/vendor/expecta -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta/templates/Specta/Specta\ Spec\ Helper.xctemplate/NSObject -I../../contrib/MagicalRecord/Project\ Files/Tests/Support/Vendor/Specta/templates/Specta/Specta\ Spec.xctemplate/Default -I../ios-sample-sdk -I../ios-sample-sdk/SampleSDK -I../ios-sample-sdk/SampleSDKResources -I../ios-sample-sdk/SASDKIntegrationTests -I../ios-sample-sdk/SASDKTests -I../ios-sample-sdk/Scripts -I../ios-sample-sdk/Swipers -I../ios-sample-sdk/SampleSDK/Core -I../ios-sample-sdk/SampleSDK/Invoice -I../ios-sample-sdk/SampleSDK/Local -I../ios-sample-sdk/SampleSDK/Payment -I../ios-sample-sdk/SampleSDK/Reader -I../ios-sample-sdk/SampleSDK/UI -I../ios-sample-sdk/SampleSDKResources/Images -I../ios-sample-sdk/SampleSDKResources/PropertyLists -I../ios-sample-sdk/SampleSDKResources/Sounds -I../ios-sample-sdk/SASDKIntegrationTests/Payment -I../ios-sample-sdk/SASDKIntegrationTests/TestHelpers -I../ios-sample-sdk/SASDKTests/Card -I../ios-sample-sdk/SASDKTests/Invoice -I../ios-sample-sdk/SASDKTests/OCMock -I../ios-sample-sdk/SASDKTests/Printing -I../ios-sample-sdk/SASDKTests/Resources -I../ios-sample-sdk/Scripts/buildMachine -I../ios-sample-sdk/Swipers/Griffin -I../ios-sample-sdk/Swipers/Magtek -I../ios-sample-sdk/Swipers/Roam -I../ios-sample-sdk/SampleSDK/Core/Analytics -I../ios-sample-sdk/SampleSDK/Core/CalLogging -I../ios-sample-sdk/SampleSDK/Core/Extensions -I../ios-sample-sdk/SampleSDK/Core/Networking -I../ios-sample-sdk/SampleSDK/Core/NSLogger -I../ios-sample-sdk/SampleSDK/Core/SecureUDID -I../ios-sample-sdk/SampleSDK/Reader/Bond -I../ios-sample-sdk/SASDKTests/OCMock/OCMock -I../ios-sample-sdk/Swipers/Magtek/Release-iphoneos -I../ios-sample-sdk/Swipers/Magtek/Release-iphonesimulator -I/Users/supermarin/Library/Developer/Xcode/DerivedData/SampleWorkspace-furbnwegumclnwbotvisbynpuqvh/Build/Intermediates/SACore.build/Debug-iphonesimulator/SACore.build/DerivedSources/i386 -I/Users/supermarin/Library/Developer/Xcode/DerivedData/SampleWorkspace-furbnwegumclnwbotvisbynpuqvh/Build/Intermediates/SACore.build/Debug-iphonesimulator/SACore.build/DerivedSources -F/Users/supermarin/Library/Developer/Xcode/DerivedData/SampleWorkspace-furbnwegumclnwbotvisbynpuqvh/Build/Products/Debug-iphonesimulator -F/Users/supermarin/Documents/source/ios-sample-newgen/libs/SACore/../../apps/iPhone/sample\ sample -F/Users/supermarin/Documents/source/ios-sample-newgen/libs/SACore/../../contrib/Star/Distributables -include /Users/supermarin/Library/Developer/Xcode/DerivedData/SampleWorkspace-furbnwegumclnwbotvisbynpuqvh/Build/Intermediates/PrecompiledHeaders/SACore-Prefix-hdwrpwhozamhpiatzyqmxqgofzdk/SACore-Prefix.pch -MMD -MT dependencies -MF /Users/supermarin/Library/Developer/Xcode/DerivedData/SampleWorkspace-furbnwegumclnwbotvisbynpuqvh/Build/Intermediates/SACore.build/Debug-iphonesimulator/SACore.build/Objects-normal/i386/SASellableItem.d --serialize-diagnostics /Users/supermarin/Library/Developer/Xcode/DerivedData/SampleWorkspace-furbnwegumclnwbotvisbynpuqvh/Build/Intermediates/SACore.build/Debug-iphonesimulator/SACore.build/Objects-normal/i386/SASellableItem.dia -c /Users/supermarin/Documents/source/ios-sample-newgen/libs/SACore/SACore/App/Models/Core\ Data/human/SASellableItem.m -o /Users/supermarin/Library/Developer/Xcode/DerivedData/SampleWorkspace-furbnwegumclnwbotvisbynpuqvh/Build/Intermediates/SACore.build/Debug-iphonesimulator/SACore.build/Objects-normal/i386/SASellableItem.o
)
SAMPLE_ANOTHER_COMPILE = %Q(
CompileC /Users/musalj/Library/Developer/Xcode/DerivedData/Kiwi-guimpeiqlepzeaankpygesetdzsx/Build/Intermediates/Kiwi.build/Debug-iphonesimulator/Kiwi.build/Objects-normal/i386/KWNull.o Classes/Core/KWNull.m normal i386 objective-c com.apple.compilers.llvm.clang.1_0.compiler
    cd /Users/musalj/code/OSS/Kiwi
    setenv LANG en_US.US-ASCII
    setenv PATH "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/Users/musalj/code/go/bin:/Users/musalj/.rbenv/shims:/Users/musalj/.rbenv/bin:/usr/local/share/npm/bin:/usr/local/bin:/Library/Python/2.7/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -x objective-c -arch i386 -fmessage-length=178 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit=0 -fcolor-diagnostics -std=c99 -fobjc-arc -Wno-trigraphs -fpascal-strings -O0 -Wno-missing-field-initializers -Wmissing-prototypes -Wno-implicit-atomic-properties -Wno-receiver-is-weak -Wno-arc-repeated-use-of-weak -Wduplicate-method-match -Wno-missing-braces -Wparentheses -Wswitch -Wunused-function -Wno-unused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wno-unknown-pragmas -Wno-shadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wbool-conversion -Wenum-conversion -Wshorten-64-to-32 -Wpointer-sign -Wnewline-eof -Wno-selector -Wno-strict-selector-match -Wundeclared-selector -Wno-deprecated-implementations -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator7.0.sdk -fexceptions -fasm-blocks -fstrict-aliasing -Wprotocol -Wdeprecated-declarations -g -Wno-sign-conversion -fobjc-abi-version=2 -fobjc-legacy-dispatch -mios-simulator-version-min=5.0 -iquote /Users/musalj/Library/Developer/Xcode/DerivedData/Kiwi-guimpeiqlepzeaankpygesetdzsx/Build/Intermediates/Kiwi.build/Debug-iphonesimulator/Kiwi.build/Kiwi-generated-files.hmap -I/Users/musalj/Library/Developer/Xcode/DerivedData/Kiwi-guimpeiqlepzeaankpygesetdzsx/Build/Intermediates/Kiwi.build/Debug-iphonesimulator/Kiwi.build/Kiwi-own-target-headers.hmap -I/Users/musalj/Library/Developer/Xcode/DerivedData/Kiwi-guimpeiqlepzeaankpygesetdzsx/Build/Intermediates/Kiwi.build/Debug-iphonesimulator/Kiwi.build/Kiwi-all-target-headers.hmap -iquote /Users/musalj/Library/Developer/Xcode/DerivedData/Kiwi-guimpeiqlepzeaankpygesetdzsx/Build/Intermediates/Kiwi.build/Debug-iphonesimulator/Kiwi.build/Kiwi-project-headers.hmap -I/Users/musalj/Library/Developer/Xcode/DerivedData/Kiwi-guimpeiqlepzeaankpygesetdzsx/Build/Products/Debug-iphonesimulator/include -I/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/include -I/Users/musalj/Library/Developer/Xcode/DerivedData/Kiwi-guimpeiqlepzeaankpygesetdzsx/Build/Intermediates/Kiwi.build/Debug-iphonesimulator/Kiwi.build/DerivedSources/i386 -I/Users/musalj/Library/Developer/Xcode/DerivedData/Kiwi-guimpeiqlepzeaankpygesetdzsx/Build/Intermediates/Kiwi.build/Debug-iphonesimulator/Kiwi.build/DerivedSources -Wall -F/Users/musalj/Library/Developer/Xcode/DerivedData/Kiwi-guimpeiqlepzeaankpygesetdzsx/Build/Products/Debug-iphonesimulator -F/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator7.0.sdk/Developer/Library/Frameworks -F/Applications/Xcode.app/Contents/Developer/Library/Frameworks -include /Users/musalj/code/OSS/Kiwi/Supporting\ Files/Kiwi-Prefix.pch -MMD -MT dependencies -MF /Users/musalj/Library/Developer/Xcode/DerivedData/Kiwi-guimpeiqlepzeaankpygesetdzsx/Build/Intermediates/Kiwi.build/Debug-iphonesimulator/Kiwi.build/Objects-normal/i386/KWNull.d --serialize-diagnostics /Users/musalj/Library/Developer/Xcode/DerivedData/Kiwi-guimpeiqlepzeaankpygesetdzsx/Build/Intermediates/Kiwi.build/Debug-iphonesimulator/Kiwi.build/Objects-normal/i386/KWNull.dia -c /Users/musalj/code/OSS/Kiwi/Classes/Core/KWNull.m -o /Users/musalj/Library/Developer/Xcode/DerivedData/Kiwi-guimpeiqlepzeaankpygesetdzsx/Build/Intermediates/Kiwi.build/Debug-iphonesimulator/Kiwi.build/Objects-normal/i386/KWNull.o
)
SAMPLE_COMPILE_CCACHE = %Q(
CompileC /Users/delisa/Library/Developer/Xcode/DerivedData/Gelato-cbyvaqwtjzrxxtfccjobyatrvvwh/Build/Intermediates.noindex/Pods.build/Release-iphoneos/Pods-Gelato-DMMTodoTxt.build/Objects-normal/armv7/DMMTodoTxtParser.o DMMTodoTxt/Pod/Classes/DMMTodoTxtParser.m normal armv7 objective-c com.apple.compilers.llvm.clang.1_0.compiler
    cd /Users/delisa/Code/personal/Gelato/Pods
    export LANG=en_US.US-ASCII
    export PATH="/Applications/Xcode/Xcode-9.3.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/usr/bin:/Applications/Xcode/Xcode-9.3.app/Contents/Developer/usr/bin:/Users/delisa/.rbenv/shims:/Users/delisa/.pyenv/shims:/Users/delisa/.swiftenv/shims:/Users/delisa/bin:/usr/local/bin:/usr/local/sbin:/bin:/usr/bin:/usr/sbin:/sbin:/Users/delisa/tmp/go/bin:/Users/delisa/Library/Android/sdk/platform-tools/"
    /Users/delisa/Code/personal/Gelato/ccache-clang -x objective-c -arch arm64 -fmessage-length=0 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit=0 -std=gnu99 -fobjc-arc -fmodules -fmodules-cache-path=/Users/delisa/Library/Developer/Xcode/DerivedData/ModuleCache.noindex -fmodules-prune-interval=86400 -fmodules-prune-after=345600 -fbuild-session-file=/Users/delisa/Library/Developer/Xcode/DerivedData/ModuleCache.noindex/Session.modulevalidation -fmodules-validate-once-per-build-session -Wnon-modular-include-in-framework-module -Werror=non-modular-include-in-framework-module -fapplication-extension -Wno-trigraphs -fpascal-strings -Os -Wno-missing-field-initializers -Wno-missing-prototypes -Wunreachable-code -Wno-implicit-atomic-properties -Wno-arc-repeated-use-of-weak -Wduplicate-method-match -Wno-missing-braces -Wparentheses -Wswitch -Wunused-function -Wno-unused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wno-unknown-pragmas -Wno-shadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wbool-conversion -Wenum-conversion -Wno-float-conversion -Wno-non-literal-null-conversion -Wno-objc-literal-conversion -Wshorten-64-to-32 -Wpointer-sign -Wno-newline-eof -Wno-selector -Wno-strict-selector-match -Wundeclared-selector -Wno-deprecated-implementations -DRELEASE=1 -DCOCOAPODS=1 -DNS_BLOCK_ASSERTIONS=1 -DOBJC_OLD_DISPATCH_PROTOTYPES=0 -isysroot /Applications/Xcode/Xcode-9.3.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS11.3.sdk -fstrict-aliasing -Wprotocol -Wdeprecated-declarations -miphoneos-version-min=9.0 -g -Wno-sign-conversion -Wno-infinite-recursion -Wno-comma -Wno-block-capture-autoreleasing -Wno-strict-prototypes -fembed-bitcode-marker -iquote /Users/delisa/Library/Developer/Xcode/DerivedData/Gelato-cbyvaqwtjzrxxtfccjobyatrvvwh/Build/Intermediates.noindex/Pods.build/Release-iphoneos/Pods-Gelato\ Today-DMMTodoTxt.build/Pods-Gelato\ Today-DMMTodoTxt-generated-files.hmap -I/Users/delisa/Library/Developer/Xcode/DerivedData/Gelato-cbyvaqwtjzrxxtfccjobyatrvvwh/Build/Intermediates.noindex/Pods.build/Release-iphoneos/Pods-Gelato\ Today-DMMTodoTxt.build/Pods-Gelato\ Today-DMMTodoTxt-own-target-headers.hmap -I/Users/delisa/Library/Developer/Xcode/DerivedData/Gelato-cbyvaqwtjzrxxtfccjobyatrvvwh/Build/Intermediates.noindex/Pods.build/Release-iphoneos/Pods-Gelato\ Today-DMMTodoTxt.build/Pods-Gelato\ Today-DMMTodoTxt-all-target-headers.hmap -iquote /Users/delisa/Library/Developer/Xcode/DerivedData/Gelato-cbyvaqwtjzrxxtfccjobyatrvvwh/Build/Intermediates.noindex/Pods.build/Release-iphoneos/Pods-Gelato\ Today-DMMTodoTxt.build/Pods-Gelato\ Today-DMMTodoTxt-project-headers.hmap -I/Users/delisa/Library/Developer/Xcode/DerivedData/Gelato-cbyvaqwtjzrxxtfccjobyatrvvwh/Build/Products/Release-iphoneos/include -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Private -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Private/DMMTodoTxt -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/APAutocompleteTextField -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/Amplitude-iOS -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/Bugsnag -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/CGFloatType -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/DMMTodoTxt -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/Dropbox-iOS-SDK -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/FMDB -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/FXKeychain -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/KSCrash -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/Kiwi -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/PDGestureTableView -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/RJBlurAlertView -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/SpinKit -I/Users/delisa/Code/personal/Gelato/Pods/Headers/Public/UIImageEffects -I/Users/delisa/Library/Developer/Xcode/DerivedData/Gelato-cbyvaqwtjzrxxtfccjobyatrvvwh/Build/Intermediates.noindex/Pods.build/Release-iphoneos/Pods-Gelato\ Today-DMMTodoTxt.build/DerivedSources/arm64 -I/Users/delisa/Library/Developer/Xcode/DerivedData/Gelato-cbyvaqwtjzrxxtfccjobyatrvvwh/Build/Intermediates.noindex/Pods.build/Release-iphoneos/Pods-Gelato\ Today-DMMTodoTxt.build/DerivedSources -F/Users/delisa/Library/Developer/Xcode/DerivedData/Gelato-cbyvaqwtjzrxxtfccjobyatrvvwh/Build/Products/Release-iphoneos -include /Users/delisa/Code/personal/Gelato/Pods/Target\ Support\ Files/Pods-Gelato\ Today-DMMTodoTxt/Pods-Gelato\ Today-DMMTodoTxt-prefix.pch -MMD -MT dependencies -MF /Users/delisa/Library/Developer/Xcode/DerivedData/Gelato-cbyvaqwtjzrxxtfccjobyatrvvwh/Build/Intermediates.noindex/Pods.build/Release-iphoneos/Pods-Gelato\ Today-DMMTodoTxt.build/Objects-normal/arm64/DMMTodoTxtParser.d --serialize-diagnostics /Users/delisa/Library/Developer/Xcode/DerivedData/Gelato-cbyvaqwtjzrxxtfccjobyatrvvwh/Build/Intermediates.noindex/Pods.build/Release-iphoneos/Pods-Gelato\ Today-DMMTodoTxt.build/Objects-normal/arm64/DMMTodoTxtParser.dia -c /Users/delisa/Code/personal/Gelato/Pods/DMMTodoTxt/Pod/Classes/DMMTodoTxtParser.m -o /Users/delisa/Library/Developer/Xcode/DerivedData/Gelato-cbyvaqwtjzrxxtfccjobyatrvvwh/Build/Intermediates.noindex/Pods.build/Release-iphoneos/Pods-Gelato\ Today-DMMTodoTxt.build/Objects-normal/arm64/DMMTodoTxtParser.o
)
SAMPLE_SWIFT_COMPILE = %Q(
CompileSwift normal arm64 /Users/paul/foo/bar/siesta/Source/Resource.swift
    cd /Users/paul/foo/bar/siesta
    /Applications/Xcode-beta.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/swift -frontend -c /Users/paul/foo/bar/siesta/Source/Networking-Alamofire.swift /Users/paul/foo/bar/siesta/Source/Networking.swift /Users/paul/foo/bar/siesta/Source/ARC+Siesta.swift /Users/paul/foo/bar/siesta/Source/DebugFormatting.swift /Users/paul/foo/bar/siesta/Source/Configuration.swift /Users/paul/foo/bar/siesta/Source/Networking-NSURLSession.swift /Users/paul/foo/bar/siesta/Source/Collection+Siesta.swift /Users/paul/foo/bar/siesta/Source/Logging.swift /Users/paul/foo/bar/siesta/Source/GCD+Siesta.swift /Users/paul/foo/bar/siesta/Source/Regex.swift /Users/paul/foo/bar/siesta/Source/ResourceObserver.swift /Users/paul/foo/bar/siesta/Source/Service.swift /Users/paul/foo/bar/siesta/Source/Request.swift /Users/paul/foo/bar/siesta/Source/NSURL+Siesta.swift /Users/paul/foo/bar/siesta/Source/ResourceStatusOverlay.swift /Users/paul/foo/bar/siesta/Source/WeakCache.swift -primary-file /Users/paul/foo/bar/siesta/Source/Resource.swift /Users/paul/foo/bar/siesta/Source/ResponseTransformer.swift /Users/paul/foo/bar/siesta/Source/RemoteImageView.swift /Users/paul/foo/bar/siesta/Source/Error.swift /Users/paul/foo/bar/siesta/Source/Entity.swift /Users/paul/foo/bar/siesta/Source/Siesta-ObjC.swift /Users/paul/foo/bar/siesta/Source/String+Siesta.swift /Users/paul/foo/bar/siesta/Source/PersistentCache.swift -target arm64-apple-ios8.0 -Xllvm -aarch64-use-tbi -enable-objc-interop -sdk /Applications/Xcode-beta.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS9.0.sdk -I /tmp/xcode-build/Release-iphoneos -F /tmp/xcode-build/Release-iphoneos -F /Users/paul/foo/bar/siesta/Carthage/Build/iOS -g -import-underlying-module -serialize-debugging-options -Xcc -I/tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/swift-overrides.hmap -Xcc -iquote -Xcc /tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/Siesta-generated-files.hmap -Xcc -I/tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/Siesta-own-target-headers.hmap -Xcc -I/tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/Siesta-all-non-framework-target-headers.hmap -Xcc -ivfsoverlay -Xcc /tmp/xcode-build/Siesta.build/all-product-headers.yaml -Xcc -iquote -Xcc /tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/Siesta-project-headers.hmap -Xcc -I/tmp/xcode-build/Release-iphoneos/include -Xcc -I/tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/DerivedSources/arm64 -Xcc -I/tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/DerivedSources -Xcc -ivfsoverlay -Xcc /tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/unextended-module-overlay.yaml -Xcc -working-directory/Users/paul/foo/bar/siesta -emit-module-doc-path /tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/Objects-normal/arm64/Resource~partial.swiftdoc -O -module-name Siesta -emit-module-path /tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/Objects-normal/arm64/Resource~partial.swiftmodule -serialize-diagnostics-path /tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/Objects-normal/arm64/Resource.dia -emit-dependencies-path /tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/Objects-normal/arm64/Resource.d -emit-reference-dependencies-path /tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/Objects-normal/arm64/Resource.swiftdeps -o /tmp/xcode-build/Siesta.build/Release-iphoneos/Siesta.build/Objects-normal/arm64/Resource.o -embed-bitcode-marker
)
SAMPLE_PRECOMPILE = %Q(
ProcessPCH /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveRecord-gxwwuvyzqubnbfaesalfplrycxpe/Build/Intermediates/PrecompiledHeaders/Pods-CocoaLumberjack-prefix-aklsecopvqdctoeroyamrkgktpei/Pods-CocoaLumberjack-prefix.pch.pch Pods-CocoaLumberjack-prefix.pch normal i386 objective-c com.apple.compilers.llvm.clang.1_0.compiler
    cd /Users/musalj/code/OSS/ObjectiveRecord/Pods
    setenv LANG en_US.US-ASCII
    setenv PATH "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/Users/musalj/.rbenv/versions/2.0.0-p247/bin:/usr/local/Cellar/rbenv/0.4.0/libexec:/Users/musalj/code/go/bin:/Users/musalj/.rbenv/shims:/Users/musalj/.rbenv/bin:/usr/local/share/npm/bin:/usr/local/bin:/Library/Python/2.7/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -x objective-c-header -arch i386 -fmessage-length=178 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit=0 -fcolor-diagnostics -std=gnu99 -fmodules -fmodules-cache-path=/Users/musalj/Library/Developer/Xcode/DerivedData/ModuleCache -Wno-trigraphs -fpascal-strings -O0 -Wno-missing-field-initializers -Wno-missing-prototypes -Werror=return-type -Wno-implicit-atomic-properties -Werror=deprecated-objc-isa-usage -Werror=objc-root-class -Wno-receiver-is-weak -Wno-arc-repeated-use-of-weak -Wno-missing-braces -Wparentheses -Wswitch -Wunused-function -Wno-unused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wno-unknown-pragmas -Wno-shadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wbool-conversion -Wenum-conversion -Wshorten-64-to-32 -Wpointer-sign -Wno-newline-eof -Wno-selector -Wno-strict-selector-match -Wundeclared-selector -Wno-deprecated-implementations -DDEBUG=1 -DCOCOAPODS=1 -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator7.0.sdk -fexceptions -fasm-blocks -fstrict-aliasing -Wprotocol -Wdeprecated-declarations -g -Wno-sign-conversion -fobjc-abi-version=2 -fobjc-legacy-dispatch -mios-simulator-version-min=6.0 -iquote /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveRecord-gxwwuvyzqubnbfaesalfplrycxpe/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-CocoaLumberjack.build/Pods-CocoaLumberjack-generated-files.hmap -I/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveRecord-gxwwuvyzqubnbfaesalfplrycxpe/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-CocoaLumberjack.build/Pods-CocoaLumberjack-own-target-headers.hmap -I/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveRecord-gxwwuvyzqubnbfaesalfplrycxpe/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-CocoaLumberjack.build/Pods-CocoaLumberjack-all-target-headers.hmap -iquote /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveRecord-gxwwuvyzqubnbfaesalfplrycxpe/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-CocoaLumberjack.build/Pods-CocoaLumberjack-project-headers.hmap -I/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveRecord-gxwwuvyzqubnbfaesalfplrycxpe/Build/Products/Debug-iphonesimulator/include -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/BuildHeaders -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/BuildHeaders/CocoaLumberjack -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/ABContactHelper -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/AFNetworking -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/CocoaLumberjack -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/CrittercismSDK -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/DTCoreText -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/DTFoundation -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/HPGrowingTextView -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/Kiwi -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/MAKVONotificationCenter -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/MAKVONotificationCenter/MAKVONotificationCenter -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/MBSwitch -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/MBSwitch/MBSwitch -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/Reachability -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/SBJson -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/SSKeychain -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/TITokenField -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/YamDrawReport -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/YamKit -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/YamKit/YamCore -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/YamKit/YamData -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/YamKit/YamSerf -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/YamKit/YamUI -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/YamKitTests -I/Users/musalj/code/OSS/ObjectiveRecord/Pods/Headers/cometclient -I/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveRecord-gxwwuvyzqubnbfaesalfplrycxpe/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-CocoaLumberjack.build/DerivedSources/i386 -I/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveRecord-gxwwuvyzqubnbfaesalfplrycxpe/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-CocoaLumberjack.build/DerivedSources -F/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveRecord-gxwwuvyzqubnbfaesalfplrycxpe/Build/Products/Debug-iphonesimulator -fobjc-arc -DOS_OBJECT_USE_OBJC=0 --serialize-diagnostics /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveRecord-gxwwuvyzqubnbfaesalfplrycxpe/Build/Intermediates/PrecompiledHeaders/Pods-CocoaLumberjack-prefix-aklsecopvqdctoeroyamrkgktpei/Pods-CocoaLumberjack-prefix.pch.dia -MMD -MT dependencies -MF /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveRecord-gxwwuvyzqubnbfaesalfplrycxpe/Build/Intermediates/PrecompiledHeaders/Pods-CocoaLumberjack-prefix-aklsecopvqdctoeroyamrkgktpei/Pods-CocoaLumberjack-prefix.pch.d -c /Users/musalj/code/OSS/ObjectiveRecord/Pods/Pods-CocoaLumberjack-prefix.pch -o /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveRecord-gxwwuvyzqubnbfaesalfplrycxpe/Build/Intermediates/PrecompiledHeaders/Pods-CocoaLumberjack-prefix-aklsecopvqdctoeroyamrkgktpei/Pods-CocoaLumberjack-prefix.pch.pch
)
SAMPLE_LIBTOOL = %Q(
Libtool /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/libPods-ObjectiveSugarTests-Kiwi.a normal i386
    cd /Users/musalj/code/OSS/ObjectiveSugar/Example/Pods
    setenv IPHONEOS_DEPLOYMENT_TARGET 5.0
    setenv PATH "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/Users/musalj/code/go/bin:/Users/musalj/.rbenv/shims:/Users/musalj/.rbenv/bin:/usr/local/share/npm/bin:/usr/local/bin:/Library/Python/2.7/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/libtool -static -arch_only i386 -syslibroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator7.0.sdk -L/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator -filelist /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-ObjectiveSugarTests-Kiwi.build/Objects-normal/i386/Pods-ObjectiveSugarTests-Kiwi.LinkFileList -framework Foundation -framework SenTestingKit -o /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/libPods-ObjectiveSugarTests-Kiwi.a
)
SAMPLE_CPRESOURCE = %Q(
CpResource ObjectiveSugar/Default-568h@2x.png /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/ObjectiveSugar.app/Default-568h@2x.png
    cd /Users/musalj/code/OSS/ObjectiveSugar/Example
    setenv PATH "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/Users/musalj/code/go/bin:/Users/musalj/.rbenv/shims:/Users/musalj/.rbenv/bin:/usr/local/share/npm/bin:/usr/local/bin:/Library/Python/2.7/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
    builtin-copy -exclude .DS_Store -exclude CVS -exclude .svn -exclude .git -exclude .hg -resolve-src-symlinks /Users/musalj/code/OSS/ObjectiveSugar/Example/ObjectiveSugar/Default-568h@2x.png /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/ObjectiveSugar.app
)
SAMPLE_COPYSTRINGS = %Q(
CopyStringsFile /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/ObjectiveSugar.app/en.lproj/InfoPlist.strings ObjectiveSugar/en.lproj/InfoPlist.strings
    cd /Users/musalj/code/OSS/ObjectiveSugar/Example
    setenv PATH "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/Users/musalj/code/go/bin:/Users/musalj/.rbenv/shims:/Users/musalj/.rbenv/bin:/usr/local/share/npm/bin:/usr/local/bin:/Library/Python/2.7/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
    builtin-copyStrings --validate --inputencoding utf-8 --outputencoding binary --outdir /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/ObjectiveSugar.app/en.lproj -- ObjectiveSugar/en.lproj/InfoPlist.strings
)
SAMPLE_PROCESS_INFOPLIST = %Q(
ProcessInfoPlistFile build/Release/The\\ Spacer.app/Contents/Info.plist The\\ Spacer/The\\ Spacer-Info.plist
    cd "/Users/delisa/Code/Personal/The Spacer"
    builtin-infoPlistUtility /Users/delisa/Code/Personal/The\\ Spacer/The\\ Spacer/The\\ Spacer-Info.plist -genpkginfo /Users/delisa/Code/Personal/The\\ Spacer/build/Release/The\\ Spacer.app/Contents/PkgInfo -expandbuildsettings -platform macosx -additionalcontentfile /Users/delisa/Code/Personal/The\\ Spacer/build/The\\ Spacer.build/Release/The\\ Spacer.build/assetcatalog_generated_info.plist -o /Users/delisa/Code/Personal/The\\ Spacer/build/Release/The\\ Spacer.app/Contents/Info.plist
)
SAMPLE_LD = %Q(
Ld /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/ObjectiveSugar.app/ObjectiveSugar normal i386
    cd /Users/musalj/code/OSS/ObjectiveSugar/Example
    setenv IPHONEOS_DEPLOYMENT_TARGET 4.3
    setenv PATH "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/Users/musalj/code/go/bin:/Users/musalj/.rbenv/shims:/Users/musalj/.rbenv/bin:/usr/local/share/npm/bin:/usr/local/bin:/Library/Python/2.7/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -arch i386 -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator7.0.sdk -L/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator -F/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator -filelist /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/Objects-normal/i386/ObjectiveSugar.LinkFileList -Xlinker -objc_abi_version -Xlinker 2 -ObjC -fobjc-arc -fobjc-link-runtime -Xlinker -no_implicit_dylibs -mios-simulator-version-min=4.3 -framework UIKit -framework Foundation -framework CoreGraphics -lPods -Xlinker -dependency_info -Xlinker /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/Objects-normal/i386/ObjectiveSugar_dependency_info.dat -o /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/ObjectiveSugar.app/ObjectiveSugar
)
SAMPLE_LD_RELATIVE = %Q(
Ld ../Build/Products/Debug-iphonesimulator/ObjectiveSugar.app/ObjectiveSugar normal i386
    cd /Users/musalj/code/OSS/ObjectiveSugar/Example
    setenv IPHONEOS_DEPLOYMENT_TARGET 4.3
    setenv PATH "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/Users/musalj/code/go/bin:/Users/musalj/.rbenv/shims:/Users/musalj/.rbenv/bin:/usr/local/share/npm/bin:/usr/local/bin:/Library/Python/2.7/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -arch i386 -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator7.0.sdk -L/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator -F/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator -filelist /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/Objects-normal/i386/ObjectiveSugar.LinkFileList -Xlinker -objc_abi_version -Xlinker 2 -ObjC -fobjc-arc -fobjc-link-runtime -Xlinker -no_implicit_dylibs -mios-simulator-version-min=4.3 -framework UIKit -framework Foundation -framework CoreGraphics -lPods -Xlinker -dependency_info -Xlinker /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/Objects-normal/i386/ObjectiveSugar_dependency_info.dat -o /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/ObjectiveSugar.app/ObjectiveSugar
).freeze
SAMPLE_DSYM = %Q(
GenerateDSYMFile /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/ObjectiveSugarTests.octest.dSYM /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/ObjectiveSugarTests.octest/ObjectiveSugarTests
    cd /Users/musalj/code/OSS/ObjectiveSugar/Example
    setenv PATH "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/Users/musalj/code/go/bin:/Users/musalj/.rbenv/shims:/Users/musalj/.rbenv/bin:/usr/local/share/npm/bin:/usr/local/bin:/Library/Python/2.7/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/dsymutil /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/ObjectiveSugarTests.octest/ObjectiveSugarTests -o /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/ObjectiveSugarTests.octest.dSYM
)
SAMPLE_TOUCH = %Q(
Touch /Users/musalj/Library/Developer/Xcode/DerivedData/Alcatraz-aobuxcinaqyzjugrnxjjhfzgwaou/Build/Products/Debug/Alcatraz\ Tests.octest
    cd /Users/musalj/code/OSS/Alcatraz
    /usr/bin/touch -c /Users/musalj/Library/Developer/Xcode/DerivedData/Alcatraz-aobuxcinaqyzjugrnxjjhfzgwaou/Build/Products/Debug/Alcatraz\ Tests.octest
)

SAMPLE_WRITE_FILE = %Q(
write-file /Users/me/myproject/Build/Intermediates/Pods.build/Debug-iphonesimulator/Pods-AFNetworking.build/Objects-normal/x86_64/Pods-AFNetworking.LinkFileList
)

SAMPLE_WRITE_AUXILIARY_FILES = %Q(Write auxiliary files)

SAMPLE_TIFFUTIL = %Q(
TiffUtil eye_icon.tiff
    cd /Users/musalj/code/OSS/Alcatraz
    /usr/bin/tiffutil -cathidpicheck /Users/musalj/code/OSS/Alcatraz/Alcatraz/eye_icon@2x.png /Users/musalj/code/OSS/Alcatraz/Alcatraz/eye_icon.png -out /Users/musalj/Library/Application\ Support/Developer/Shared/Xcode/Plug-ins/Alcatraz.xcplugin/Contents/Resources/eye_icon.tiff
)
SAMPLE_RUN_SCRIPT = %Q(
PhaseScriptExecution Check\\ Pods\\ Manifest.lock /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/Script-468DABF301EC4EC1A00CC4C2.sh
    cd /Users/musalj/code/OSS/ObjectiveSugar/Example
    setenv ACTION build
    setenv AD_HOC_CODE_SIGNING_ALLOWED NO
    setenv ALTERNATE_GROUP staff
    setenv ALTERNATE_MODE u+w,go-w,a+rX
    setenv ALTERNATE_OWNER musalj
    setenv ALWAYS_SEARCH_USER_PATHS NO
    setenv ALWAYS_USE_SEPARATE_HEADERMAPS YES
    setenv APPLE_INTERNAL_DEVELOPER_DIR /AppleInternal/Developer
    setenv APPLE_INTERNAL_DIR /AppleInternal
    setenv APPLE_INTERNAL_DOCUMENTATION_DIR /AppleInternal/Documentation
    setenv APPLE_INTERNAL_LIBRARY_DIR /AppleInternal/Library
    setenv APPLE_INTERNAL_TOOLS /AppleInternal/Developer/Tools
    setenv APPLY_RULES_IN_COPY_FILES NO
    setenv ARCHS i386
    setenv ARCHS_STANDARD i386
    setenv ARCHS_STANDARD_32_64_BIT i386
    setenv ARCHS_STANDARD_32_BIT i386
    setenv ARCHS_STANDARD_64_BIT x86_64
    setenv ARCHS_STANDARD_INCLUDING_64_BIT "i386 x86_64"
    setenv AVAILABLE_PLATFORMS "iphonesimulator macosx iphoneos"
    setenv BUILD_COMPONENTS "headers build"
    setenv BUILD_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products
    setenv BUILD_ROOT /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products
    setenv BUILD_STYLE
    setenv BUILD_VARIANTS normal
    setenv BUILT_PRODUCTS_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator
    setenv CACHE_ROOT /var/folders/dp/3hrp_j_j04b6gxk5tybq0lz80000gp/C/com.apple.DeveloperTools/5.0.2-5A3005/Xcode
    setenv CCHROOT /var/folders/dp/3hrp_j_j04b6gxk5tybq0lz80000gp/C/com.apple.DeveloperTools/5.0.2-5A3005/Xcode
    setenv CHMOD /bin/chmod
    setenv CHOWN /usr/sbin/chown
    setenv CLANG_CXX_LANGUAGE_STANDARD gnu++0x
    setenv CLANG_CXX_LIBRARY libc++
    setenv CLANG_ENABLE_OBJC_ARC YES
    setenv CLANG_WARN_CONSTANT_CONVERSION YES
    setenv CLANG_WARN_EMPTY_BODY YES
    setenv CLANG_WARN_ENUM_CONVERSION YES
    setenv CLANG_WARN_INT_CONVERSION YES
    setenv CLANG_WARN__DUPLICATE_METHOD_MATCH YES
    setenv CLASS_FILE_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/JavaClasses
    setenv CLEAN_PRECOMPS YES
    setenv CLONE_HEADERS NO
    setenv CODESIGNING_FOLDER_PATH /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/ObjectiveSugar.app
    setenv CODE_SIGNING_ALLOWED NO
    setenv CODE_SIGN_CONTEXT_CLASS XCiPhoneSimulatorCodeSignContext
    setenv CODE_SIGN_IDENTITY "iPhone Developer"
    setenv COLOR_DIAGNOSTICS NO
    setenv COMBINE_HIDPI_IMAGES NO
    setenv COMMAND_MODE legacy
    setenv COMPOSITE_SDK_DIRS /var/folders/dp/3hrp_j_j04b6gxk5tybq0lz80000gp/C/com.apple.DeveloperTools/5.0.2-5A3005/Xcode/CompositeSDKs
    setenv COMPRESS_PNG_FILES YES
    setenv CONFIGURATION Debug
    setenv CONFIGURATION_BUILD_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator
    setenv CONFIGURATION_TEMP_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator
    setenv CONTENTS_FOLDER_PATH ObjectiveSugar.app
    setenv COPYING_PRESERVES_HFS_DATA NO
    setenv COPY_PHASE_STRIP NO
    setenv COPY_RESOURCES_FROM_STATIC_FRAMEWORKS YES
    setenv CP /bin/cp
    setenv CREATE_INFOPLIST_SECTION_IN_BINARY NO
    setenv CURRENT_ARCH i386
    setenv CURRENT_VARIANT normal
    setenv DEAD_CODE_STRIPPING NO
    setenv DEBUGGING_SYMBOLS YES
    setenv DEBUG_INFORMATION_FORMAT dwarf-with-dsym
    setenv DEFAULT_COMPILER com.apple.compilers.llvm.clang.1_0
    setenv DEFAULT_KEXT_INSTALL_PATH /System/Library/Extensions
    setenv DEPLOYMENT_LOCATION NO
    setenv DEPLOYMENT_POSTPROCESSING NO
    setenv DERIVED_FILES_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/DerivedSources
    setenv DERIVED_FILE_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/DerivedSources
    setenv DERIVED_SOURCES_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/DerivedSources
    setenv DEVELOPER_APPLICATIONS_DIR /Applications/Xcode.app/Contents/Developer/Applications
    setenv DEVELOPER_BIN_DIR /Applications/Xcode.app/Contents/Developer/usr/bin
    setenv DEVELOPER_DIR /Applications/Xcode.app/Contents/Developer
    setenv DEVELOPER_FRAMEWORKS_DIR /Applications/Xcode.app/Contents/Developer/Library/Frameworks
    setenv DEVELOPER_FRAMEWORKS_DIR_QUOTED /Applications/Xcode.app/Contents/Developer/Library/Frameworks
    setenv DEVELOPER_LIBRARY_DIR /Applications/Xcode.app/Contents/Developer/Library
    setenv DEVELOPER_SDK_DIR /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs
    setenv DEVELOPER_TOOLS_DIR /Applications/Xcode.app/Contents/Developer/Tools
    setenv DEVELOPER_USR_DIR /Applications/Xcode.app/Contents/Developer/usr
    setenv DEVELOPMENT_LANGUAGE English
    setenv DOCUMENTATION_FOLDER_PATH ObjectiveSugar.app/English.lproj/Documentation
    setenv DO_HEADER_SCANNING_IN_JAM NO
    setenv DSTROOT /tmp/ObjectiveSugar.dst
    setenv DT_TOOLCHAIN_DIR /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain
    setenv DWARF_DSYM_FILE_NAME ObjectiveSugar.app.dSYM
    setenv DWARF_DSYM_FILE_SHOULD_ACCOMPANY_PRODUCT NO
    setenv DWARF_DSYM_FOLDER_PATH /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator
    setenv EFFECTIVE_PLATFORM_NAME -iphonesimulator
    setenv ENABLE_HEADER_DEPENDENCIES YES
    setenv EXCLUDED_INSTALLSRC_SUBDIRECTORY_PATTERNS ".DS_Store .svn .git .hg CVS"
    setenv EXCLUDED_RECURSIVE_SEARCH_PATH_SUBDIRECTORIES "*.nib *.lproj *.framework *.gch (*) .DS_Store CVS .svn .git .hg *.xcodeproj *.xcode *.pbproj *.pbxproj"
    setenv EXECUTABLES_FOLDER_PATH ObjectiveSugar.app/Executables
    setenv EXECUTABLE_FOLDER_PATH ObjectiveSugar.app
    setenv EXECUTABLE_NAME ObjectiveSugar
    setenv EXECUTABLE_PATH ObjectiveSugar.app/ObjectiveSugar
    setenv FILE_LIST /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/Objects/LinkFileList
    setenv FIXED_FILES_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/FixedFiles
    setenv FRAMEWORKS_FOLDER_PATH ObjectiveSugar.app/Frameworks
    setenv FRAMEWORK_FLAG_PREFIX -framework
    setenv FRAMEWORK_SEARCH_PATHS "/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator "
    setenv FRAMEWORK_VERSION A
    setenv FULL_PRODUCT_NAME ObjectiveSugar.app
    setenv GCC3_VERSION 3.3
    setenv GCC_C_LANGUAGE_STANDARD gnu99
    setenv GCC_DYNAMIC_NO_PIC NO
    setenv GCC_INLINES_ARE_PRIVATE_EXTERN YES
    setenv GCC_OBJC_LEGACY_DISPATCH YES
    setenv GCC_OPTIMIZATION_LEVEL 0
    setenv GCC_PFE_FILE_C_DIALECTS "c objective-c c++ objective-c++"
    setenv GCC_PRECOMPILE_PREFIX_HEADER YES
    setenv GCC_PREFIX_HEADER ObjectiveSugar/ObjectiveSugar-Prefix.pch
    setenv GCC_PREPROCESSOR_DEFINITIONS "DEBUG=1  COCOAPODS=1"
    setenv GCC_SYMBOLS_PRIVATE_EXTERN NO
    setenv GCC_TREAT_WARNINGS_AS_ERRORS NO
    setenv GCC_VERSION com.apple.compilers.llvm.clang.1_0
    setenv GCC_VERSION_IDENTIFIER com_apple_compilers_llvm_clang_1_0
    setenv GCC_WARN_ABOUT_RETURN_TYPE YES
    setenv GCC_WARN_UNINITIALIZED_AUTOS YES
    setenv GCC_WARN_UNUSED_VARIABLE YES
    setenv GENERATE_MASTER_OBJECT_FILE NO
    setenv GENERATE_PKGINFO_FILE YES
    setenv GENERATE_PROFILING_CODE NO
    setenv GID 20
    setenv GROUP staff
    setenv HEADERMAP_INCLUDES_FLAT_ENTRIES_FOR_TARGET_BEING_BUILT YES
    setenv HEADERMAP_INCLUDES_FRAMEWORK_ENTRIES_FOR_ALL_PRODUCT_TYPES YES
    setenv HEADERMAP_INCLUDES_NONPUBLIC_NONPRIVATE_HEADERS YES
    setenv HEADERMAP_INCLUDES_PROJECT_HEADERS YES
    setenv HEADER_SEARCH_PATHS "/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/include \"/Users/musalj/code/OSS/ObjectiveSugar/Example/Pods/Headers\" \"/Users/musalj/code/OSS/ObjectiveSugar/Example/Pods/Headers/Kiwi\" \"/Users/musalj/code/OSS/ObjectiveSugar/Example/Pods/Headers/ObjectiveSugar\""
    setenv ICONV /usr/bin/iconv
    setenv INFOPLIST_EXPAND_BUILD_SETTINGS YES
    setenv INFOPLIST_FILE ObjectiveSugar/ObjectiveSugar-Info.plist
    setenv INFOPLIST_OUTPUT_FORMAT binary
    setenv INFOPLIST_PATH ObjectiveSugar.app/Info.plist
    setenv INFOPLIST_PREPROCESS NO
    setenv INFOSTRINGS_PATH ObjectiveSugar.app/English.lproj/InfoPlist.strings
    setenv INSTALL_DIR /tmp/ObjectiveSugar.dst/Applications
    setenv INSTALL_GROUP staff
    setenv INSTALL_MODE_FLAG u+w,go-w,a+rX
    setenv INSTALL_OWNER musalj
    setenv INSTALL_PATH /Applications
    setenv INSTALL_ROOT /tmp/ObjectiveSugar.dst
    setenv IPHONEOS_DEPLOYMENT_TARGET 4.3
    setenv JAVAC_DEFAULT_FLAGS "-J-Xms64m -J-XX:NewSize=4M -J-Dfile.encoding=UTF8"
    setenv JAVA_APP_STUB /System/Library/Frameworks/JavaVM.framework/Resources/MacOS/JavaApplicationStub
    setenv JAVA_ARCHIVE_CLASSES YES
    setenv JAVA_ARCHIVE_TYPE JAR
    setenv JAVA_COMPILER /usr/bin/javac
    setenv JAVA_FOLDER_PATH ObjectiveSugar.app/Java
    setenv JAVA_FRAMEWORK_RESOURCES_DIRS Resources
    setenv JAVA_JAR_FLAGS cv
    setenv JAVA_SOURCE_SUBDIR .
    setenv JAVA_USE_DEPENDENCIES YES
    setenv JAVA_ZIP_FLAGS -urg
    setenv JIKES_DEFAULT_FLAGS "+E +OLDCSO"
    setenv KEEP_PRIVATE_EXTERNS NO
    setenv LD_DEPENDENCY_INFO_FILE /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/Objects-normal/i386/ObjectiveSugar_dependency_info.dat
    setenv LD_GENERATE_MAP_FILE NO
    setenv LD_MAP_FILE_PATH /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/ObjectiveSugar-LinkMap-normal-i386.txt
    setenv LD_NO_PIE NO
    setenv LD_QUOTE_LINKER_ARGUMENTS_FOR_COMPILER_DRIVER YES
    setenv LEGACY_DEVELOPER_DIR /Applications/Xcode.app/Contents/PlugIns/Xcode3Core.ideplugin/Contents/SharedSupport/Developer
    setenv LEX lex
    setenv LIBRARY_FLAG_NOSPACE YES
    setenv LIBRARY_FLAG_PREFIX -l
    setenv LIBRARY_KEXT_INSTALL_PATH /Library/Extensions
    setenv LIBRARY_SEARCH_PATHS "/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator "
    setenv LINKER_DISPLAYS_MANGLED_NAMES NO
    setenv LINK_FILE_LIST_normal_i386 /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/Objects-normal/i386/ObjectiveSugar.LinkFileList
    setenv LINK_WITH_STANDARD_LIBRARIES YES
    setenv LOCALIZED_RESOURCES_FOLDER_PATH ObjectiveSugar.app/English.lproj
    setenv LOCAL_ADMIN_APPS_DIR /Applications/Utilities
    setenv LOCAL_APPS_DIR /Applications
    setenv LOCAL_DEVELOPER_DIR /Library/Developer
    setenv LOCAL_LIBRARY_DIR /Library
    setenv MACH_O_TYPE mh_execute
    setenv MACOSX_DEPLOYMENT_TARGET 10.6
    setenv MAC_OS_X_PRODUCT_BUILD_VERSION 13A603
    setenv MAC_OS_X_VERSION_ACTUAL 1090
    setenv MAC_OS_X_VERSION_MAJOR 1090
    setenv MAC_OS_X_VERSION_MINOR 0900
    setenv MODULE_CACHE_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ModuleCache
    setenv NATIVE_ARCH i386
    setenv NATIVE_ARCH_32_BIT i386
    setenv NATIVE_ARCH_64_BIT x86_64
    setenv NATIVE_ARCH_ACTUAL x86_64
    setenv NO_COMMON YES
    setenv OBJC_ABI_VERSION 2
    setenv OBJECT_FILE_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/Objects
    setenv OBJECT_FILE_DIR_normal /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/Objects-normal
    setenv OBJROOT /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates
    setenv ONLY_ACTIVE_ARCH NO
    setenv OPTIMIZATION_LEVEL 0
    setenv OS MACOS
    setenv OSAC /usr/bin/osacompile
    setenv OTHER_LDFLAGS -ObjC
    setenv PACKAGE_TYPE com.apple.package-type.wrapper.application
    setenv PASCAL_STRINGS YES
    setenv PATH "/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin:/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/libexec:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/local/bin:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/usr/bin:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/usr/local/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/local/bin:/Applications/Xcode.app/Contents/Developer/Tools:/Users/musalj/code/go/bin:/Users/musalj/.rbenv/shims:/Users/musalj/.rbenv/bin:/usr/local/share/npm/bin:/usr/local/bin:/Library/Python/2.7/bin:/usr/bin:/bin:/usr/sbin:/sbin"
    setenv PATH_PREFIXES_EXCLUDED_FROM_HEADER_DEPENDENCIES "/usr/include /usr/local/include /System/Library/Frameworks /System/Library/PrivateFrameworks /Applications/Xcode.app/Contents/Developer/Headers /Applications/Xcode.app/Contents/Developer/SDKs /Applications/Xcode.app/Contents/Developer/Platforms"
    setenv PBDEVELOPMENTPLIST_PATH ObjectiveSugar.app/pbdevelopment.plist
    setenv PFE_FILE_C_DIALECTS objective-c
    setenv PKGINFO_FILE_PATH /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/PkgInfo
    setenv PKGINFO_PATH ObjectiveSugar.app/PkgInfo
    setenv PLATFORM_DEVELOPER_APPLICATIONS_DIR /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Applications
    setenv PLATFORM_DEVELOPER_BIN_DIR /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin
    setenv PLATFORM_DEVELOPER_LIBRARY_DIR /Applications/Xcode.app/Contents/PlugIns/Xcode3Core.ideplugin/Contents/SharedSupport/Developer/Library
    setenv PLATFORM_DEVELOPER_SDK_DIR /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs
    setenv PLATFORM_DEVELOPER_TOOLS_DIR /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Tools
    setenv PLATFORM_DEVELOPER_USR_DIR /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr
    setenv PLATFORM_DIR /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform
    setenv PLATFORM_NAME iphonesimulator
    setenv PLATFORM_PREFERRED_ARCH i386
    setenv PLATFORM_PRODUCT_BUILD_VERSION 11B508
    setenv PLATFORM_VERSION_AVAILABILITY_H_FORMAT 70000
    setenv PLIST_FILE_OUTPUT_FORMAT binary
    setenv PLUGINS_FOLDER_PATH ObjectiveSugar.app/PlugIns
    setenv PODS_ROOT /Users/musalj/code/OSS/ObjectiveSugar/Example/Pods
    setenv PRECOMPS_INCLUDE_HEADERS_FROM_BUILT_PRODUCTS_DIR YES
    setenv PRECOMP_DESTINATION_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/PrefixHeaders
    setenv PRESERVE_DEAD_CODE_INITS_AND_TERMS NO
    setenv PRIVATE_HEADERS_FOLDER_PATH ObjectiveSugar.app/PrivateHeaders
    setenv PRODUCT_NAME ObjectiveSugar
    setenv PRODUCT_SETTINGS_PATH /Users/musalj/code/OSS/ObjectiveSugar/Example/ObjectiveSugar/ObjectiveSugar-Info.plist
    setenv PRODUCT_TYPE com.apple.product-type.application
    setenv PROFILING_CODE NO
    setenv PROJECT ObjectiveSugar
    setenv PROJECT_DERIVED_FILE_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/DerivedSources
    setenv PROJECT_DIR /Users/musalj/code/OSS/ObjectiveSugar/Example
    setenv PROJECT_FILE_PATH /Users/musalj/code/OSS/ObjectiveSugar/Example/ObjectiveSugar.xcodeproj
    setenv PROJECT_NAME ObjectiveSugar
    setenv PROJECT_TEMP_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build
    setenv PROJECT_TEMP_ROOT /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates
    setenv PUBLIC_HEADERS_FOLDER_PATH ObjectiveSugar.app/Headers
    setenv RECURSIVE_SEARCH_PATHS_FOLLOW_SYMLINKS YES
    setenv REMOVE_CVS_FROM_RESOURCES YES
    setenv REMOVE_GIT_FROM_RESOURCES YES
    setenv REMOVE_HG_FROM_RESOURCES YES
    setenv REMOVE_SVN_FROM_RESOURCES YES
    setenv REZ_COLLECTOR_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/ResourceManagerResources
    setenv REZ_OBJECTS_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/ResourceManagerResources/Objects
    setenv REZ_SEARCH_PATHS "/Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator "
    setenv SCAN_ALL_SOURCE_FILES_FOR_INCLUDES NO
    setenv SCRIPTS_FOLDER_PATH ObjectiveSugar.app/Scripts
    setenv SCRIPT_INPUT_FILE_COUNT 0
    setenv SCRIPT_OUTPUT_FILE_COUNT 0
    setenv SDKROOT /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator7.0.sdk
    setenv SDK_DIR /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator7.0.sdk
    setenv SDK_NAME iphonesimulator7.0
    setenv SDK_PRODUCT_BUILD_VERSION 11B508
    setenv SED /usr/bin/sed
    setenv SEPARATE_STRIP NO
    setenv SEPARATE_SYMBOL_EDIT NO
    setenv SET_DIR_MODE_OWNER_GROUP YES
    setenv SET_FILE_MODE_OWNER_GROUP NO
    setenv SHALLOW_BUNDLE YES
    setenv SHARED_DERIVED_FILE_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator/DerivedSources
    setenv SHARED_FRAMEWORKS_FOLDER_PATH ObjectiveSugar.app/SharedFrameworks
    setenv SHARED_PRECOMPS_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/PrecompiledHeaders
    setenv SHARED_SUPPORT_FOLDER_PATH ObjectiveSugar.app/SharedSupport
    setenv SKIP_INSTALL NO
    setenv SOURCE_ROOT /Users/musalj/code/OSS/ObjectiveSugar/Example
    setenv SRCROOT /Users/musalj/code/OSS/ObjectiveSugar/Example
    setenv STRINGS_FILE_OUTPUT_ENCODING binary
    setenv STRIP_INSTALLED_PRODUCT YES
    setenv STRIP_STYLE all
    setenv SUPPORTED_DEVICE_FAMILIES "1 2"
    setenv SUPPORTED_PLATFORMS "iphonesimulator iphoneos"
    setenv SYMROOT /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products
    setenv SYSTEM_ADMIN_APPS_DIR /Applications/Utilities
    setenv SYSTEM_APPS_DIR /Applications
    setenv SYSTEM_CORE_SERVICES_DIR /System/Library/CoreServices
    setenv SYSTEM_DEMOS_DIR /Applications/Extras
    setenv SYSTEM_DEVELOPER_APPS_DIR /Applications/Xcode.app/Contents/Developer/Applications
    setenv SYSTEM_DEVELOPER_BIN_DIR /Applications/Xcode.app/Contents/Developer/usr/bin
    setenv SYSTEM_DEVELOPER_DEMOS_DIR "/Applications/Xcode.app/Contents/Developer/Applications/Utilities/Built Examples"
    setenv SYSTEM_DEVELOPER_DIR /Applications/Xcode.app/Contents/Developer
    setenv SYSTEM_DEVELOPER_DOC_DIR "/Applications/Xcode.app/Contents/Developer/ADC Reference Library"
    setenv SYSTEM_DEVELOPER_GRAPHICS_TOOLS_DIR "/Applications/Xcode.app/Contents/Developer/Applications/Graphics Tools"
    setenv SYSTEM_DEVELOPER_JAVA_TOOLS_DIR "/Applications/Xcode.app/Contents/Developer/Applications/Java Tools"
    setenv SYSTEM_DEVELOPER_PERFORMANCE_TOOLS_DIR "/Applications/Xcode.app/Contents/Developer/Applications/Performance Tools"
    setenv SYSTEM_DEVELOPER_RELEASENOTES_DIR "/Applications/Xcode.app/Contents/Developer/ADC Reference Library/releasenotes"
    setenv SYSTEM_DEVELOPER_TOOLS /Applications/Xcode.app/Contents/Developer/Tools
    setenv SYSTEM_DEVELOPER_TOOLS_DOC_DIR "/Applications/Xcode.app/Contents/Developer/ADC Reference Library/documentation/DeveloperTools"
    setenv SYSTEM_DEVELOPER_TOOLS_RELEASENOTES_DIR "/Applications/Xcode.app/Contents/Developer/ADC Reference Library/releasenotes/DeveloperTools"
    setenv SYSTEM_DEVELOPER_USR_DIR /Applications/Xcode.app/Contents/Developer/usr
    setenv SYSTEM_DEVELOPER_UTILITIES_DIR /Applications/Xcode.app/Contents/Developer/Applications/Utilities
    setenv SYSTEM_DOCUMENTATION_DIR /Library/Documentation
    setenv SYSTEM_KEXT_INSTALL_PATH /System/Library/Extensions
    setenv SYSTEM_LIBRARY_DIR /System/Library
    setenv TARGETED_DEVICE_FAMILY 1
    setenv TARGETNAME ObjectiveSugar
    setenv TARGET_BUILD_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Products/Debug-iphonesimulator
    setenv TARGET_NAME ObjectiveSugar
    setenv TARGET_TEMP_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build
    setenv TEMP_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build
    setenv TEMP_FILES_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build
    setenv TEMP_FILE_DIR /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build
    setenv TEMP_ROOT /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates
    setenv TOOLCHAINS com.apple.dt.toolchain.XcodeDefault
    setenv UID 502
    setenv UNLOCALIZED_RESOURCES_FOLDER_PATH ObjectiveSugar.app
    setenv UNSTRIPPED_PRODUCT NO
    setenv USER musalj
    setenv USER_APPS_DIR /Users/musalj/Applications
    setenv USER_LIBRARY_DIR /Users/musalj/Library
    setenv USE_DYNAMIC_NO_PIC YES
    setenv USE_HEADERMAP YES
    setenv USE_HEADER_SYMLINKS NO
    setenv VALIDATE_PRODUCT NO
    setenv VALID_ARCHS i386
    setenv VERBOSE_PBXCP NO
    setenv VERSIONPLIST_PATH ObjectiveSugar.app/version.plist
    setenv VERSION_INFO_BUILDER musalj
    setenv VERSION_INFO_FILE ObjectiveSugar_vers.c
    setenv VERSION_INFO_STRING "\"@(#)PROGRAM:ObjectiveSugar  PROJECT:ObjectiveSugar-\""
    setenv WRAPPER_EXTENSION app
    setenv WRAPPER_NAME ObjectiveSugar.app
    setenv WRAPPER_SUFFIX .app
    setenv XCODE_APP_SUPPORT_DIR /Applications/Xcode.app/Contents/Developer/Library/Xcode
    setenv XCODE_PRODUCT_BUILD_VERSION 5A3005
    setenv XCODE_VERSION_ACTUAL 0502
    setenv XCODE_VERSION_MAJOR 0500
    setenv XCODE_VERSION_MINOR 0500
    setenv XPCSERVICES_FOLDER_PATH ObjectiveSugar.app/XPCServices
    setenv YACC yacc
    setenv arch i386
    setenv variant normal
    /bin/sh -c /Users/musalj/Library/Developer/Xcode/DerivedData/ObjectiveSugar-ayzdhqmmwtqgysdpznmovjlupqjy/Build/Intermediates/ObjectiveSugar.build/Debug-iphonesimulator/ObjectiveSugar.build/Script-468DABF301EC4EC1A00CC4C2.sh
)
SAMPLE_ANALYZE = %Q(
Analyze CocoaChip/CCChip8DisplayView.m
    cd /Users/dustin/Source/CocoaChip
    setenv LANG en_US.US-ASCII
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -x objective-c -arch x86_64 -fmessage-length=107 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit=0 -fcolor-diagnostics -std=gnu99 -fobjc-arc -Wno-trigraphs -fpascal-strings -Os -Werror -Wmissing-field-initializers -Wmissing-prototypes -Wno-implicit-atomic-properties -Wno-receiver-is-weak -Wno-arc-repeated-use-of-weak -Wduplicate-method-match -Wmissing-braces -Wparentheses -Wswitch -Wunused-function -Wunused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wno-unknown-pragmas -Wshadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wbool-conversion -Wenum-conversion -Wsign-compare -Wshorten-64-to-32 -Wpointer-sign -Wnewline-eof -Wno-selector -Wstrict-selector-match -Wundeclared-selector -Wno-deprecated-implementations -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk -fasm-blocks -fstrict-aliasing -Wprotocol -Wdeprecated-declarations -mmacosx-version-min=10.7 -g -fvisibility=hidden -Wno-sign-conversion -D__clang_analyzer__ -Xclang -analyzer-output=plist-multi-file -Xclang -analyzer-config -Xclang path-diagnostics-alternate=true -Xclang -analyzer-config -Xclang report-in-main-source-file=true -Xclang -analyzer-checker -Xclang security.insecureAPI.UncheckedReturn -Xclang -analyzer-checker -Xclang security.insecureAPI.getpw -Xclang -analyzer-checker -Xclang security.insecureAPI.gets -Xclang -analyzer-checker -Xclang security.insecureAPI.mkstemp -Xclang -analyzer-checker -Xclang security.insecureAPI.mktemp -Xclang -analyzer-disable-checker -Xclang security.insecureAPI.rand -Xclang -analyzer-disable-checker -Xclang security.insecureAPI.strcpy -Xclang -analyzer-checker -Xclang security.insecureAPI.vfork -iquote /Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/CocoaChip-generated-files.hmap -I/Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/CocoaChip-own-target-headers.hmap -I/Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/CocoaChip-all-target-headers.hmap -iquote /Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/CocoaChip-project-headers.hmap -I/Users/dustin/Source/CocoaChip/build/Release/include -I/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/include -I/Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/DerivedSources/x86_64 -I/Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/DerivedSources -F/Users/dustin/Source/CocoaChip/build/Release -include /var/folders/nn/s88k060x7016ccml2bc6f5_h0000gn/C/com.apple.DeveloperTools/5.0.2-5A3005/Xcode/SharedPrecompiledHeaders/CocoaChip-Prefix-cqqikmscxiepjgdcrgpysqicucyu/CocoaChip-Prefix.pch --analyze /Users/dustin/Source/CocoaChip/CocoaChip/CCChip8DisplayView.m -o /Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/StaticAnalyzer/CocoaChip/CocoaChip/normal/x86_64/CCChip8DisplayView.plist
)
SAMPLE_ANALYZE_SHALLOW = %Q(
AnalyzeShallow CocoaChip/CCChip8DisplayView.m
    cd /Users/dustin/Source/CocoaChip
    setenv LANG en_US.US-ASCII
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -x objective-c -arch x86_64 -fmessage-length=107 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit=0 -fcolor-diagnostics -std=gnu99 -fobjc-arc -Wno-trigraphs -fpascal-strings -Os -Werror -Wmissing-field-initializers -Wmissing-prototypes -Wno-implicit-atomic-properties -Wno-receiver-is-weak -Wno-arc-repeated-use-of-weak -Wduplicate-method-match -Wmissing-braces -Wparentheses -Wswitch -Wunused-function -Wunused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wno-unknown-pragmas -Wshadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wbool-conversion -Wenum-conversion -Wsign-compare -Wshorten-64-to-32 -Wpointer-sign -Wnewline-eof -Wno-selector -Wstrict-selector-match -Wundeclared-selector -Wno-deprecated-implementations -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk -fasm-blocks -fstrict-aliasing -Wprotocol -Wdeprecated-declarations -mmacosx-version-min=10.7 -g -fvisibility=hidden -Wno-sign-conversion -D__clang_analyzer__ -Xclang -analyzer-output=plist-multi-file -Xclang -analyzer-config -Xclang path-diagnostics-alternate=true -Xclang -analyzer-config -Xclang report-in-main-source-file=true -Xclang -analyzer-config -Xclang mode=shallow -Xclang -analyzer-checker -Xclang security.insecureAPI.UncheckedReturn -Xclang -analyzer-checker -Xclang security.insecureAPI.getpw -Xclang -analyzer-checker -Xclang security.insecureAPI.gets -Xclang -analyzer-checker -Xclang security.insecureAPI.mkstemp -Xclang -analyzer-checker -Xclang security.insecureAPI.mktemp -Xclang -analyzer-disable-checker -Xclang security.insecureAPI.rand -Xclang -analyzer-disable-checker -Xclang security.insecureAPI.strcpy -Xclang -analyzer-checker -Xclang security.insecureAPI.vfork -iquote /Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/CocoaChip-generated-files.hmap -I/Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/CocoaChip-own-target-headers.hmap -I/Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/CocoaChip-all-target-headers.hmap -iquote /Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/CocoaChip-project-headers.hmap -I/Users/dustin/Source/CocoaChip/build/Release/include -I/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/include -I/Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/DerivedSources/x86_64 -I/Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/DerivedSources -F/Users/dustin/Source/CocoaChip/build/Release -include /var/folders/nn/s88k060x7016ccml2bc6f5_h0000gn/C/com.apple.DeveloperTools/5.0.2-5A3005/Xcode/SharedPrecompiledHeaders/CocoaChip-Prefix-dzmrdzscqkbvvrafvxsbjwbxtmlz/CocoaChip-Prefix.pch --analyze /Users/dustin/Source/CocoaChip/CocoaChip/CCChip8DisplayView.m -o /Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/StaticAnalyzer/CocoaChip/CocoaChip/normal/x86_64/CCChip8DisplayView.plist
)
SAMPLE_ANALYZE_CPP = %Q(
Analyze CocoaChip/CCChip8DisplayView.cpp
    cd /Users/dustin/Source/CocoaChip
    setenv LANG en_US.US-ASCII
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -x objective-c -arch x86_64 -fmessage-length=107 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit=0 -fcolor-diagnostics -std=gnu99 -fobjc-arc -Wno-trigraphs -fpascal-strings -Os -Werror -Wmissing-field-initializers -Wmissing-prototypes -Wno-implicit-atomic-properties -Wno-receiver-is-weak -Wno-arc-repeated-use-of-weak -Wduplicate-method-match -Wmissing-braces -Wparentheses -Wswitch -Wunused-function -Wunused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wno-unknown-pragmas -Wshadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wbool-conversion -Wenum-conversion -Wsign-compare -Wshorten-64-to-32 -Wpointer-sign -Wnewline-eof -Wno-selector -Wstrict-selector-match -Wundeclared-selector -Wno-deprecated-implementations -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk -fasm-blocks -fstrict-aliasing -Wprotocol -Wdeprecated-declarations -mmacosx-version-min=10.7 -g -fvisibility=hidden -Wno-sign-conversion -D__clang_analyzer__ -Xclang -analyzer-output=plist-multi-file -Xclang -analyzer-config -Xclang path-diagnostics-alternate=true -Xclang -analyzer-config -Xclang report-in-main-source-file=true -Xclang -analyzer-checker -Xclang security.insecureAPI.UncheckedReturn -Xclang -analyzer-checker -Xclang security.insecureAPI.getpw -Xclang -analyzer-checker -Xclang security.insecureAPI.gets -Xclang -analyzer-checker -Xclang security.insecureAPI.mkstemp -Xclang -analyzer-checker -Xclang security.insecureAPI.mktemp -Xclang -analyzer-disable-checker -Xclang security.insecureAPI.rand -Xclang -analyzer-disable-checker -Xclang security.insecureAPI.strcpy -Xclang -analyzer-checker -Xclang security.insecureAPI.vfork -iquote /Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/CocoaChip-generated-files.hmap -I/Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/CocoaChip-own-target-headers.hmap -I/Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/CocoaChip-all-target-headers.hmap -iquote /Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/CocoaChip-project-headers.hmap -I/Users/dustin/Source/CocoaChip/build/Release/include -I/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/include -I/Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/DerivedSources/x86_64 -I/Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/DerivedSources -F/Users/dustin/Source/CocoaChip/build/Release -include /var/folders/nn/s88k060x7016ccml2bc6f5_h0000gn/C/com.apple.DeveloperTools/5.0.2-5A3005/Xcode/SharedPrecompiledHeaders/CocoaChip-Prefix-cqqikmscxiepjgdcrgpysqicucyu/CocoaChip-Prefix.pch --analyze /Users/dustin/Source/CocoaChip/CocoaChip/CCChip8DisplayView.m -o /Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/StaticAnalyzer/CocoaChip/CocoaChip/normal/x86_64/CCChip8DisplayView.plist
).freeze
SAMPLE_COMPILE_XIB = %Q(
CompileXIB CocoaChip/en.lproj/MainMenu.xib
    cd /Users/dustin/Source/CocoaChip
    setenv XCODE_DEVELOPER_USR_PATH /Applications/Xcode.app/Contents/Developer/usr/bin/..
    /Applications/Xcode.app/Contents/Developer/usr/bin/ibtool --errors --warnings --notices --minimum-deployment-target 10.7 --output-format human-readable-text --compile /Users/dustin/Source/CocoaChip/build/Release/CocoaChip.app/Contents/Resources/en.lproj/MainMenu.nib /Users/dustin/Source/CocoaChip/CocoaChip/en.lproj/MainMenu.xib
)
SAMPLE_COMPILE_STORYBOARD = %Q(
CompileStoryboard sample/Main.storyboard
    cd /Users/chipp/Developer/sample
    export XCODE_DEVELOPER_USR_PATH=/Applications/Xcode.app/Contents/Developer/usr/bin/..
    /Applications/Xcode.app/Contents/Developer/usr/bin/ibtool --target-device iphone --target-device ipad --errors --warnings --notices --module sample --minimum-deployment-target 7.0 --output-partial-info-plist /Users/chipp/Library/Developer/Xcode/DerivedData/sample-etjztiverddwaddrudeteewjzfxw/Build/Intermediates/ArchiveIntermediates/sample/IntermediateBuildFilesPath/sample.build/Release-iphoneos/sample.build/SJMetroPickerStoryboard-SBPartialInfo.plist --auto-activate-custom-fonts --output-format human-readable-text --compilation-directory /Users/chipp/Library/Developer/Xcode/DerivedData/sample-etjztiverddwaddrudeteewjzfxw/Build/Intermediates/ArchiveIntermediates/sample/InstallationBuildProductsLocation/Applications/sample.app /Users/chipp/Developer/sample/sample/Main.storyboard
)
SAMPLE_CODESIGN = %Q(
CodeSign build/Release/CocoaChip.app
    cd /Users/dustin/Source/CocoaChip
    setenv CODESIGN_ALLOCATE /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/codesign_allocate
    Using code signing identity \"Mac Developer: John Smith (0123456789)\"
    /usr/bin/codesign --force --sign 0123456789ABCDEF0123456789ABCDEF01234567 /Users/dustin/Source/CocoaChip/build/Release/CocoaChip.app
)
SAMPLE_CODESIGN_FRAMEWORK = %Q(
CodeSign build/Release/CocoaChipCore.framework/Versions/A
    cd /Users/dustin/Source/CocoaChip
    setenv CODESIGN_ALLOCATE /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/codesign_allocate
    Using code signing identity "Mac Developer: John Smith (0123456789)"
    /usr/bin/codesign --force --sign 0123456789ABCDEF0123456789ABCDEF01234567 /Users/dustin/Source/CocoaChip/build/Release/CocoaChipCore.framework/Versions/A
)
SAMPLE_PREPROCESS = %Q(
Preprocess build/CocoaChip.build/Release/CocoaChip.build/Preprocessed-Info.plist CocoaChip/CocoaChip-Info.plist
    cd /Users/dustin/Source/CocoaChip
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/cc -E -P -x c -Wno-trigraphs /Users/dustin/Source/CocoaChip/CocoaChip/CocoaChip-Info.plist -F/Users/dustin/Source/CocoaChip/build/Release -o /Users/dustin/Source/CocoaChip/build/CocoaChip.build/Release/CocoaChip.build/Preprocessed-Info.plist
)
SAMPLE_PBXCP = %Q(
PBXCp build/Release/CocoaChipCore.framework build/Release/CocoaChip.app/Contents/Frameworks/CocoaChipCore.framework
    cd /Users/dustin/Source/CocoaChip
    builtin-copy -exclude .DS_Store -exclude CVS -exclude .svn -exclude .git -exclude .hg -strip-debug-symbols -strip-tool /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/strip -resolve-src-symlinks /Users/dustin/Source/CocoaChip/build/Release/CocoaChipCore.framework /Users/dustin/Source/CocoaChip/build/Release/CocoaChip.app/Contents/Frameworks
warning: skipping copy phase strip, binary is code signed: /Users/dustin/Source/CocoaChip/build/Release/CocoaChipCore.framework/Versions/A/CocoaChipCore
)

SAMPLE_SCREENSHOT_FILE = 'RACCommandSpec_enabled_signal_should_send_YES_while_executing_is_YES.png'
SAMPLE_UNRELATED_IMAGE_FILE = 'apple_raw.png'

################################################################################
# ERRORS
################################################################################

SAMPLE_PODS_ERROR = "error: The sandbox is not in sync with the Podfile.lock. Run 'pod install' or update your CocoaPods installation."

SAMPLE_COMPILE_ERROR = %Q(
/Users/musalj/code/OSS/SampleApp/SampleTest.m:12:59: error: expected identifier
                [[thread.lastMessage should] equal:thread.];
                                                          ^
)

SAMPLE_FILE_MISSING_ERROR = %Q(
<unknown>:0: error: no such file or directory: '/Users/travis/build/supermarin/project/Classes/Class + Category/Two Words/MissingViewController.swift'
)

SAMPLE_CODESIGN_ERROR = %Q(
Code Sign error: No code signing identites found: No valid signing identities (i.e. certificate and private key pair) matching the team ID ‚ÄúCAT6HF57NJ‚Äù were found.
)

SAMPLE_CODESIGN_ERROR_NO_SPACES = %Q(
CodeSign error: code signing is required for product type 'Application' in SDK 'iOS 7.0'
)

SAMPLE_REQUIRES_PROVISION = %Q(
PROJECT_NAME requires a provisioning profile. Select a provisioning profile for the "Debug" build configuration in the project editor.
)

SAMPLE_NO_CERTIFICATE = %Q(
No certificate matching 'iPhone Distribution: Name (B89SBB0AV9)' for team 'B89SBB0AV9':  Select a different signing certificate for CODE_SIGN_IDENTITY, a team that matches your selected certificate, or switch to automatic provisioning.
)

SAMPLE_FATAL_COMPILE_ERROR = %Q(
In file included from /Users/musalj/code/OSS/SampleApp/Pods/SuperCoolPod/SuperAwesomeClass.m:12:
In file included from /Users/musalj/code/OSS/SampleApp/Pods/../LessCoolPod/LessCoolClass.h:9:
In file included from /Users/musalj/code/OSS/SampleApp/Pods/../LessCoolPod/EvenLessCoolClass.h:10:
/Users/musalj/code/OSS/SampleApp/Pods/Headers/LessCoolPod/SomeRandomClass.h:31:9: fatal error: 'SomeRandomHeader.h' file not found
#import "SomeRandomHeader.h"
        ^
)

SAMPLE_FATAL_COMPILE_PCH_ERROR = %Q(
fatal error: file '/path/to/myproject/Pods/Pods-environment.h' has been modified since the precompiled header '/Users/hiroshi/Library/Developer/Xcode/DerivedData/MyProject-gfmuvpipjscewkdnqacgumhfarrd/Build/Intermediates/PrecompiledHeaders/MyProject-Prefix-dwjpvcnrlaydzmegejmcvrtcfkpf/MyProject-Prefix.pch.pch' was built
)

SAMPLE_FATAL_HEADER_ERROR = %Q(
fatal error: malformed or corrupted AST file: 'could not find file '/Users/mpv/dev/project/Crashlytics.framework/Headers/Crashlytics.h' referenced by AST file' note: after modifying system headers, please delete the module cache at '/Users/mpv/Library/Developer/Xcode/DerivedData/ModuleCache/M5WJ0FYE7N06'
)

SAMPLE_UNDEFINED_SYMBOLS = %Q(
Undefined symbols for architecture x86_64:
  "_OBJC_CLASS_$_CABasicAnimation", referenced from:
      objc-class-ref in ATZRadialProgressControl.o
ld: symbol(s) not found for architecture x86_64
)

SAMPLE_DUPLICATE_SYMBOLS = %Q(
duplicate symbol _OBJC_IVAR_$ClassName._ivarName in:
    /Users/username/Library/Developer/Xcode/DerivedData/App-arcyyktezaigixbocjwfhsjllojz/Build/Intermediates/App.build/Debug-iphonesimulator/App.build/Objects-normal/i386/ClassName.o
    /Users/username/Library/Developer/Xcode/DerivedData/App-arcyyktezaigixbocjwfhsjllojz/Build/Products/Debug-iphonesimulator/libPods.a(DuplicateClassName.o)
ld: 1 duplicate symbol for architecture i386
clang: error: linker command failed with exit code 1 (use -v to see invocation)
)

SAMPLE_BITCODE_LD = %Q(
ld: '/Users/.../GoogleAnalytics-iOS-SDK/libGoogleAnalyticsServices.a(TAGHit.o)' does not contain bitcode. You must rebuild it with bitcode enabled (Xcode setting ENABLE_BITCODE), obtain an updated library from the vendor, or disable bitcode for this target. for architecture armv7
)

SAMPLE_LD_SYMBOLS_ERROR = 'ld: symbol(s) not found for architecture x86_64'
SAMPLE_LD_LIBRARY_ERROR = 'ld: library not found for -lPods-Yammer'

SAMPLE_CLANG_ERROR = 'clang: error: linker command failed with exit code 1 (use -v to see invocation)'

SAMPLE_COMPILE_ERROR_WITH_TILDES = %Q(
/Users/musalj/code/OSS/ObjectiveSugar/Example/ObjectiveSugarTests/NSSetTests.m:93:16: error: no visible @interface for 'NSArray' declares the selector 'shoulds'
            }] shoulds] equal:@[ @"F458 Italia", @"Testarossa" ]];
            ~~ ^~~~~~~
)

SAMPLE_PROFILE_DOESNT_SUPPORT_CAPABILITY_ERROR = %Q(
Provisioning profile "Profile Name" doesn't support the Push Notifications capability.
)

SAMPLE_PROFILE_DOESNT_INCLUDE_ENTITLEMENT_ERROR = %Q(
Provisioning profile "Profile Name" doesn't include the aps-environment entitlement.
)

SAMPLE_CODE_SIGNING_IS_REQUIRED_ERROR = %Q(
Code signing is required for product type 'Application' in SDK 'iOS 10.0'
)

SAMPLE_NO_PROFILE_MATCHING_ERROR = %Q(
No profile matching 'TargetName' found:  Xcode couldn't find a profile matching 'TargetName'. Install the profile (by dragging and dropping it onto Xcode's dock item) or select a different one in the General tab of the target editor.
)

SAMPLE_SWIFT_UNAVAILABLE = "Swift is unavailable on iOS earlier than 7.0; please set IPHONEOS_DEPLOYMENT_TARGET to 7.0 or later (currently it is '6.0')."

SAMPLE_USE_LEGACY_SWIFT = "“Use Legacy Swift Language Version” (SWIFT_VERSION) is required to be configured correctly for targets which use Swift. Use the [Edit > Convert > To Current Swift Syntax…] menu to choose a Swift version or use the Build Settings editor to configure the build setting directly."

SAMPLE_MODULE_INCLUDES_ERROR = "<module-includes>:1:1: error: umbrella header for module 'ModuleName' does not include header 'Header.h'"

################################################################################
# WARNINGS
################################################################################

SAMPLE_FORMAT_WARNING = %Q(
/Users/supermarin/code/oss/ObjectiveSugar/Example/ObjectiveSugar/AppDelegate.m:19:31: warning: format specifies type 'id' but the argument has type 'int' [-Wformat]
    NSLog(@"I HAZ %@ CATS", 1);
                         ~~   ^
                         %d
1 warning generated.
)

SAMPLE_WILL_NOT_BE_CODE_SIGNED = "FrameworkName will not be code signed because its settings don't specify a development team."
SAMPLE_FORMAT_OTHER_UNRECOGNIZED_STRING = "This is a string that won't be matched by any other regex."

