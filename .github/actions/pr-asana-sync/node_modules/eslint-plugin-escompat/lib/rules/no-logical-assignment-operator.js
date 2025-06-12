'use strict';

module.exports = (context, badBrowser) => ({
  'AssignmentExpression[operator="||="]'(node) {
    context.report(node, `Logical assignment operators are not supported in ${badBrowser}`)
  },
  'AssignmentExpression[operator="&&="]'(node) {
    context.report(node, `Logical assignment operators are not supported in ${badBrowser}`)
  },
  'AssignmentExpression[operator="??="]'(node) {
    context.report(node, `Logical assignment operators are not supported in ${badBrowser}`)
  }
})
