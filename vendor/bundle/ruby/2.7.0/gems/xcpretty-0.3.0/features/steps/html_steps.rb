Then(/^I should see a test suite section in HTML$/) do
  html_test_suites.first.should_not be_nil
end

Then(/^I should see a failed test in HTML$/) do
  html_report_body.get_elements("//*[contains(@class, 'test failing')]/").to_a.size.should_not == 0
end

Then(/^the failure counter should show (\d+) tests?$/) do |fail_count|
  html_report_body.get_elements("//*[@id='fail-count']/").first.elements.to_a.first.text.to_i.should == fail_count.to_i
end

Then(/^I should see a passing test in HTML$/) do
  html_report_body.get_elements("//*[contains(@class, 'test passing')]/").to_a.size.should_not == 0
end

Then(/^I should see (\d+) tests in HTML$/) do |test_count|
  html_report_body.get_elements("//*[contains(@class, 'test ')]/").size.should == test_count.to_i
end

Then(/^I should see (\d+) test suite sections? in HTML$/) do |section_count|
  html_test_suites.size.should == section_count.to_i
end

Then(/^I should see a screenshot in HTML$/) do
  html_report_body.get_elements("//*[contains(@class, 'screenshot')]/").to_a.size.should_not == 0
end

Then(/^I should not see a screenshot in HTML$/) do
  html_report_body.get_elements("//*[contains(@class, 'screenshot')]/").to_a.size.should == 0
end

