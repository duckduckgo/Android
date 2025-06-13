'use strict';

module.exports = (context, badBrowser) => ({
  'ImportExpression, CallExpression[callee.type="Import"]'(node) {
    context.report(node, `Dynamic import is not supported in ${badBrowser}`)
  }
})
