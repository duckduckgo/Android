## Matcher Definitions

First of all we have the concept of 'Matcher Definitions', which are small pieces of configuration that
describe how a matcher works at runtime.

For example, the following `email` definition states that it will use 3 different matching `strategies` at runtime.

- `cssSelector`
- `ddgMatcher`
- `vendorRegex`

Under `strategies`, the keys of the object match up to a strategy name, and the values are references to the particular
named item.

```json
{
  "matchers": {
    "fields": {
      "email": {
        "type": "email",
        "strategies": {
          "cssSelector": "email",
          "ddgMatcher": "email",
          "vendorRegex": "email"
        }
      }
    }
  }
}
```

Matcher definitions are just that, definitions - they cannot perform work alone. They serve only as a declarative
format for specifying which strategies should be executed and in which order.

Where you see `"cssSelector": "email"` on a field, you can assume that there's a strategy called `cssSelector`
and that it has a field called `email`.

In the configuration, it will look like this.

```json
{
  "strategies": {
    "cssSelector": {
      "selectors": {
        "email": "input:not([type])[name*=mail i]:not([readonly]):not([disabled]):not([hidden]):not([aria-hidden=true])"
      }
    }
  }
}
```

---

A similar format will exist for `ddgMatcher` and `vendorRegex` too.

## CSS Strategy format

The CSS strategy is straight forward as it's just a CSS selector

```json
{ "firstName": "[name*=fname i], [autocomplete*=given-name i]" }
```

## DDG Strategy format

The DDG matcher contains a `match` regex and an optional `not` regex too.

**NOTE**: The DDG matcher currently only operates on the following:

- label text
- autocomplete attribute
- related text

The `name` attribute is missing here because of historical reasons. When the multiple matching strategies were implemented
the DDG regexes 

```json
{ "match": "(first|given|fore).?name" }
```

## Vendor Regex Strategy

For the vendor regexes, we are *mostly* using the regexes provided by the [firefox codebase](https://searchfox.org/mozilla-central/source/toolkit/components/formautofill/content/heuristicsRegexp.js) 
- we aim to roughly maintain their format so that we can easily compare updates in the fiuture. 

---

## Putting it together

With those 2 pieces of information, we can say that at runtime if we want to match an input as an `email` field, then we
have the opportunity to execute any of the 3 strategies (or all of them!).

The ordering will be determined by a private class field on the `Matching` class.

```javascript
/** @type {Array<StrategyNames>} */
#defaultStrategyOrder = ['cssSelector', 'ddgMatcher', 'vendorRegex']
```

The following is pseudocode, but it demonstrates the lookup nature of the matcher definition -> strategies

```javascript
// loop through all strategy names
for (let strategy of this.#defaultStrategyOrder) {
    
    // for each strategy, now loop through given fields
    for (let field of fields) {
        
        // does this field reference this particular strategy? 
        const lookup = matcher.strategies[strategy];
        
        // if it does and strategy = `cssSelector`, execute the logic for `cssSelector`  
        if (lookup && strategy === "cssSelector") {
            const css = config.strategies.cssSelector.selectors[lookup];
            if (inputElement.matches(css)) {
                return true
            }
        }
        
        // repeat for other strategies...
    }
}
```

## Future-proofing and Remote configuration

Because this system was carefully designed to accept a declarative configuration format, it will enable modifications
and additions to be easily applied and tested since the bulk of the changes would just be to the configuration only.

For example, if we ever needed to configure the matching order on a field, a CSS selectors or any of the regexes,
then we can simply apply a patch to the configuration directly, without touching the application logic.

Although Remote Configuration of this feature will be tackled separately, the work I've done was deliberately designed
in a way that will make these sorts of patches possible in the future. 


## Matching Logic

Matching logic runs on lists of Matchers, known in the codebase as `MatcherLists`. For example, the `id` matcher list
consists of `firstname`, `middlename`, `lastname` etc.

This means that when trying to infer the type of input, we need to consider the fact that each individual matcher
may or may not implement a given strategy.

For example, this table uses a ✅ to indicate that a matcher uses a particular strategy:

| type       | cssSelector | ddgRegex | vendorRegex |
|------------|-------------|----------|-------------|
| firstname  | ✅           | ✅        | ✅           |
| middlename | ✅           | ❌        | ✅           |
| lastname   | ✅           | ✅        | ✅           |


In that example, the execution order of mathcing would be the following:

- (`cssSelector`) firstname
- (`cssSelector`) lastname
- (`cssSelector`) middlename
- (`ddgRegex`) firstname
- (`ddgRegex`) lastname
- (`vendorRegex`) firstname
- (`vendorRegex`) middlename
- (`vendorRegex`) lastname


You can see that the `cssSelector` selector strategy is attempted for each field before moving onto `ddgRegex` and then 
to `vendorRegex`
