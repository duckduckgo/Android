# Playwright testing for Autofill

You'll find a growing number of tests within `integration-test/tests` - these tests
are intended to be scripted as a user would interact with our application.

## Intention

The integration tests in this project serve 2 primary purposes

**1) They help during development**

Because you can script common flows that you'd normally test manually anyway, you end up being efficient with your time as you'll need to open native applications far fewer times during the day

**2) They act as automated QA within our workflows / release cycles**

These tests encode the most common/important user flows into executable scripts, this is a massive 
help when running locally, but becomes even more so when running on every Pull Request. 

They are **not** a replacement for manual testing - instead they fill the gap between unit tests
and manual tests.

### Test files

These live within `integration-test/tests`

  - the files are named with the following format `<feature>.<platform>.spec.js`
  - this format matches the configuration inside `playwright.config.js`

### Mocking

Cross-platform mocking is achieved via implementations of `MockBuilder`. This is done
to offer an API that reads well and can be replicated for each platform. For example, mocking 
the user flow of having a private+personal email address on both webkit + android would look
like the following: 

```javascript
// webkit mocks
await createWebkitMocks()
    .withPrivateEmail("x")
    .withPersonalEmail("y")
    .applyTo(page)

// android mocks
await createAndroidMocks()
    .withPrivateEmail("x")
    .withPersonalEmail("y")
    .applyTo(page)
```

All future mocks should uphold this constraint of conforming to the `MockBuilder` interface. You can see
the first two implementations of mocking in `integration-test/helpers/mocks.js` 

### Page Helpers

Create wrappers/helpers for known test pages so that duplicated is reduced across test files. Inside
`integration-test/helpers/mocks.js` you'll see there's already wrappers around 3 different test
pages and their intention is to hide implementation details (like selectors etc) away from the actual
test files.

In this example, the creator of the test file does not need to know how to navigate to the correct
page, nor the specific details of what `clickIntoInput` does - it's about readability of tests. These
methods should be named in a styles that matches what the application does, or what you expect it to do.

```js
const emailPage = emailAutofillPage(page, server)
await emailPage.navigate()
await emailPage.clickIntoInput()
```

## How to run locally

```shell
npm run test:integration
```

---

# Workflows

## Add a new test

- Either add a new file in `integration-test/tests`, or just copy/paste an existing one for the
platform that you are targeting.
- Decide on what you need to mock: what data does this test require? (use existing examples as a guide)
- Write your script: use the page helpers if they exist, or create your own. Always aim for the test scripts to read as a user would interact with the application. When you have logic that makes that difficult, abstract it away! This is what separates integration tests from unit tests.

## Debug a failing CI test run

When a test has failed in Github Actions, you can download the artifact produced and use the [Trace Viewer](https://playwright.dev/docs/trace-viewer) to debug any problems.

## Debug a failing test

Because tests run in parallel (for speed), failures can be tricky to isolate, follow these steps:

1) First, set `workers: 1` in `playwright.config.js` - this will cause the tests to run sequentially, making it immediately obvious which file had the problem
2) Once you've discovered the file with the flaxy/broken test, isolate further by using `.only` if needed, eg: 
    ```javascript
    test.only("autofill email", () => {
        // snip
    })
    ```
3) now you can re-run this single test in isolation
4) if you want to click around the page, and open devtools etc, add a `await page.pause()` at a place in the test script where you'd like it to pause
   ```javascript
   await emailPage.navigate();
   await page.pause() // <-- add this
   // other steps won't execute now
   ```
