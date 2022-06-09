## Tracking improvements and regressions of Real-world Tests

Since we cannot offer a 100% perfect solution in terms of input classification, we should instead
aim to create a system that allows us to accurately measure improvement and regressions over time.

For example, we can configure our test of Twitter's login & signup forms with the following configuration.

```javascript
// src/Form/test-cases/index.js
module.exports = [
    // snip
    { html: 'twitter_login.html' },
    { html: 'twitter_signup.html', expectedFailures: ['birthdayMonth', 'birthdayDay', 'birthdayYear'] },
    // snip
]
```

Notice how we are being explicit about `3` known failures in the sign-up forms' html with `expectedFailures`.
This is in contrast to the login form, in which we expect `0` failures. (the absence of the `expectedFailures` has the same meaning as it being empty)

This gives a clear indication of what is known to be broken, and what is working as expected. 

Then, as we work on individual features, it will be become very clear if we've improved the matching, or if we've made it worse. Either way, this testing setup forces you to either address the regressions, add new exceptions, or remove old ones that are no longer valid. 

### Improvements

This is the test output you'd see if you've **improved** the matching on `twitter_signup.html`

    Test twitter_signup.html should contain 3 known failures: ['birthdayMonth', 'birthdayDay', 'birthdayYear'], found 0

This means you can remove the `expectedFailures` from the test configuration and it's clear in the git log that an improvement was made

### Regressions

If a change has reduced the accuracy of matching, for example on `twitter_login` from above, where no failures where expected, you'll get this output instead

    Test twitter_signup.html should NOT contain failures, found 1 ["email"]

At that point it's very clear what has broken, and you can work to resolve the issue before filing a PR.


## Testing Tips

To run a single real-world test case, you can do the following

```shell
# Run costco_checkout.html real-world test only
./node_modules/.bin/jest --verbose=false -t 'costco_checkout.html'
```

To produce test results as an HTML file, run the following. This is particularly useful
when multiple tests have changed status, and you want an overview.

```shell
# This will create `test-report.html` in the root of the project which can be opened in a browser 
npm run test:report
```
