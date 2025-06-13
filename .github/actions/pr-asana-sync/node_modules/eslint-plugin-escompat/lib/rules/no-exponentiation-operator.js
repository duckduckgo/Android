'use strict';

module.exports = (context, badBrowser) => ({
  'AssignmentExpression[operator="**="], BinaryExpression[operator="**"]'(node) {
    context.report(node, `Exponentiation Operator is not supported in ${badBrowser}`)
  }
})
