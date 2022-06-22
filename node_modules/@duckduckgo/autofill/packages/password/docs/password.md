## DDG Password API

This module exposes a single `generate()` method.

You can do the following with it

1: Generate a password based on DuckDuckGo's default settings
2: Generate a password from a 'passwordrules' attribute string
3: Lookup known password rules from the [resources provided by Apple](https://github.com/apple/password-manager-resources)


```javascript
const password = require("@duckduckgo/autofill/packages/password");
const rules = require("@duckduckgo/autofill/packages/password/rules.json");

// generate a password with default settings
const pw = password.generate()

// generate a password with a given input, falling back to default
const pw = password.generate({ input: "minlength: 30; required: lower, upper;"})

// generate a password with rules from a known domain,
// if it exists in the given rules, falling back to default
const pw = password.generate({ domain: "example.com", rules })
```

The API is designed to **never** throw an exception, it will always fall back to the default ruleset if there's
anything wrong with the `input` or `domain`.

## Password rules

This library includes a snapshot of [this file](https://github.com/apple/password-manager-resources/blob/main/quirks/password-rules.json) that you are free to include in your calls to `generate`. You'll need to require the file (in whichever way your bundler needs it) and pass it along with the `domain`. 

```javascript
const pw = password.generate({ 
    domain: "example.com",
    rules: require("@duckduckgo/autofill/packages/password/rules.json"),
})
```

This gives you the flexibility to add/remove rules for each domain as you see fit - if this file was automatically included, then calls to `generate` would be bound to whatever was in that file. 

**Example:** Here's how you could merge the base rules with some of your own

```javascript
const customRules = {
    "example.com": { "password-rules": "min-length: 30; required: upper, lower, digit" },
    "example.eu": { "password-rules": "min-length: 40; required: upper, lower, digit" }
}

// use the base rules, overriding/adding customRules.
const pw = password.generate({
    domain: "example.com",
    rules: {
        ...require("@duckduckgo/autofill/packages/password/rules.json"),
        ...customRules
    },
})
```

Or, to remove rules for a given domain (where rules may have changed due to a backend update), you can do the following:

```javascript
const {
    ['autify.com']: _autify,
    ['axa.de']: _axa,
    ...rules } = require("@duckduckgo/autofill/packages/password/rules.json");

// this will fallback to default, since `autify.com` was removed from the ruleset  
const pw = password.generate({
    domain: "autify.com",
    rules,
})
```

## Error handling

This public API will never throw an exception - it's designed to *always* produce a password. During development however, you may want more feedback about an input that might be incorrect - for
that you can provide an `onError` callback to observe any thrown exceptions. 

```javascript
const pw = password.generate({ 
    domain: "localhost:8080",
    rules: require("@duckduckgo/autofill/packages/password/rules.json"),
    onError: (e) => {
        console.error(e)
    }
})
```

# DDG Default Rules

With no parameters, the generate function will use the following character set

```
abcdefghijkmnopqrstuvwxyzABCDEFGHIJKLMNPQRSTUVWXYZ0123456789
```

Along with the following password rules

```
minlength: 20; maxlength: 30;
```

Which currently produces passwords in this format:

```
N3xeFEQf3yiXy3V1msa2
iHjs07Xj64nWfiNrm1nB
pN14zIYhSE0Q6iFuAhcd
QEu6bhPA0MhZ0BhrkaWI
iKub0kcgzrUFdfWdGKdg
jnhChxEtZ7tU4dhUTaHw
DDXNhKaSv7ufabwKfeLP
VmJ1IdUmvqERkdEY2I7A
8xKpY2NsLf4dn1zUMinB
I08cqi3ZyQz3mQHevfNU
```

These passwords have roughly 119 bits of entropy (`Math.log2(62**20)`), which is very secure.

When password rules dictate a max-length, this becomes drastically reduced. For example if a site dictates that it only allows a maxlength of 8 chars, then we only get about 47 bits of entropy with the character set above.

Note, these are all 20 characters because the generation continues until it finds a password that suits
the rule - in this case the rule is the basic character set so is always matched.

For example, the following rules

```
minlength: 10; maxlength: 30; required: [$]; required: upper,lower,digit;
```

Would return results like this, all about 12 chars long, because it took an extra 2 cycles to 
find a matching password with the required `$`

```
XZJZUt$8cBp2
6$7WwRcg9GkJ
PKJFCe$j0UYZ
eZLE$bHMoh50
9vPyhM$QS1D9
km99pG$GJJXR
sPMpe$AP7svJ
YY$7Dymsi26f
qg$WaLvfGeVh
mGW9m2D97Z$x
```
