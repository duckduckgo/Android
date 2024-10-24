Given(/^I have tests in my suite from 2 classes$/) do
  add_run_input SAMPLE_OCUNIT_TEST
  add_run_input SAMPLE_KIWI_TEST
end

When(/^I pipe to xcpretty with "(.*?)" and specify a custom path$/) do |args|
  step("I pipe to xcpretty with \"#{args} --output #{custom_report_path}\"")
end

When(/^I pipe to xcpretty with two custom "(.*?)" report paths$/) do |type|
  step("I pipe to xcpretty with \"--report #{type} --output #{custom_report_path} --report #{type} --output #{other_custom_report_path}\"")
end

Then(/^I should have test reports in two custom paths$/) do
  step("I should have a test report at \"#{custom_report_path}\"")
  step("I should have a test report at \"#{other_custom_report_path}\"")
end

Then(/^I should have a test report in a custom path$/) do
  step("I should have a test report at \"#{custom_report_path}\"")
end

When(/^I pipe to xcpretty with a custom reporter$/) do
  reporter_path = File.expand_path('../../../spec/fixtures/custom_reporter.rb', __FILE__)
  run_xcpretty("-r #{reporter_path}")
end

