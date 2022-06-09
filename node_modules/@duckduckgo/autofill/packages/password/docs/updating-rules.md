## Rules

We include the following [file](https://raw.githubusercontent.com/apple/password-manager-resources/main/quirks/password-rules.json) within this
package, which allows password generation to respect known rules for the websites listed.

## Update the rules

Inside this packages folder, `packages/password`, run the following

```shell
npm run rules:update
```

## Github Action

We use a Github action to notify new PR's with a comment when our ruleset has
become out of sync with Apple's.

It's in the following file: 
- `.github/workflows/password-rules.yml`
 
It uses scripts from 
- `packages/password/scripts/rules.js`

The script itself does not update anything, it's just there as a notice to prompt
anyone watching the repo that an update may be required.
