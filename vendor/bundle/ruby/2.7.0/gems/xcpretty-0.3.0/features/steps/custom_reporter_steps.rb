Then(/^I should see a passing test in my custom report$/) do
  custom_report.should include("WOW such PASS.")
end

Then(/^I should see a failed test in my custom report$/) do
  custom_report.should include("WOW such FAIL.")
end

Then(/^I should see a pending test in my custom report$/) do
  custom_report.should include("WOW such PENDING.")
end

Then(/^the custom failure counter should show (\d+) tests?$/) do |fail_count|
  custom_report.should include("Much 1 FAIL.")
end

