# v3.0.0

## Added

 * Block scope matchers can accept a trailing `*` to optionally match blocks by prefix #35

## Breaking

 * Block matchers no longer match prefixes of blocks by default, can now be configured via options #35

# v2.6.0

 * Disable auto fixing by default, allow it to be optionally enabled. #26

# v2.5.0

 * Add support for auto fixing violations - #19 @tgreen7

# v2.4.0

 * Add support for defining 2 levels deep in blocks (ie. `ava.default`)

# v2.3.1

 * Bump js-yaml from 3.13.0 to 3.13.1 due to security vulnerability - #11

# v2.3.0

 * Allow test block names to be specified in options - #10

# v2.2.0

 * Added rule for catching `.only` blocks for `serial` - #9 @IevgenRagulin

# v2.1.0

 * Added rule for catching `.only` blocks for `fixture` - #8 @roughy

# v2.0.1

 * Fixed major bug where rule would cause errors for objects with key `only` - #7 @bendemboski

# v2.0.0

 * Updated rule format to ESLint 3
 * Updated ESLInt dependency to `>=3.0.0`
 * Updated node engine to `>=4.0.0`
 * Get CircleCI up and running

# v1.2.0

 * Added rules for catching `.only` blocks for `test`, `context` and `tape`

# v1.1.0

 * Updated rule to use `Identifier` rather than `CallExpression`
 * Changed reporter to give a more generic message (removed reference to mocha)
 * Added additional test coverage

# v1.0.1

 * Added additional test coverage
 * Removed unnecessary dependencies in `package.json`

# v1.0.0

 * Initial version
