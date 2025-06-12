'use strict';

module.exports = (context, badBrowser) => ({
  'LogicalExpression[operator="??"]'(node) {
    context.report(node, `the Nullish Coalescing Operator is not supported in ${badBrowser}`)
  }
})
