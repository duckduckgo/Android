'use strict';

module.exports = (context, badBrowser) => ({
  BindExpression(node) {
    context.report(node, `The Bind Operator is not supported in ${badBrowser}`)
  }
})
