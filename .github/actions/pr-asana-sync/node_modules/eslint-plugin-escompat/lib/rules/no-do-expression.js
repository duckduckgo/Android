'use strict';

module.exports = (context, badBrowser) => ({
  DoExpression(node) {
    context.report(node, `Do Expressions are not supported in ${badBrowser}`)
  }
})
