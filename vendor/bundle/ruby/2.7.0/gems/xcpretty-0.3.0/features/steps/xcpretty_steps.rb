When(/^I run xcpretty$/) do
  @output = `bin/xcpretty 2>&1`
end

When(/^I run xcpretty with (.*)$/) do |flags|
  @output = `bin/xcpretty #{flags}`
end

When(/^I run xcpretty over a big file$/) do
  start_time = Time.now
  @output = `cat features/fixtures/xcodebuild.log | bin/xcpretty -c`
  @xcpretty_run_time = Time.now - start_time
end

Then(/^I should see the help banner$/) do
  run_output.should include("Usage: xcodebuild [options] | xcpretty")
end

Then(/^I should see the xcpretty version$/) do
  run_output.should include(XCPretty::VERSION)
end

Then(/^the exit status code should be (\d)$/) do |numbah|
  $?.exitstatus.should == numbah.to_i
end

Then(/^the performance should be way faster than running cat$/) do
  puts "XCPretty run time: #{@xcpretty_run_time}"
  @xcpretty_run_time.should < 2
end

