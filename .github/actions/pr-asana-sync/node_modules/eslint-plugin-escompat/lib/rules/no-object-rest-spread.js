'use strict';

module.exports = (context, badBrowser) => ({
  'ObjectExpression > SpreadElement'(node) {
    context.report(node, `Object Rest/Spread is not supported in ${badBrowser}`)
  },
  'ObjectPattern > RestElement'(node) {
    context.report(node, `Object Rest/Spread is not supported in ${badBrowser}`)
  },

  // Catch older versions of eslint and babel-eslint
  ExperimentalRestProperty(node) {
    context.report(node, `Object Rest/Spread is not supported in ${badBrowser}`)
  },
  ExperimentalSpreadProperty(node) {
    context.report(node, `Object Rest/Spread is not supported in ${badBrowser}`)
  },
})
