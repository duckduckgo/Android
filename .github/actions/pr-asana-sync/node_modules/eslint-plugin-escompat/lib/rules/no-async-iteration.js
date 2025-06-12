'use strict';

module.exports = (context, badBrowser) => ({
  'ForOfStatement[await=true]'(node) {
    context.report(node, `Async Iteration is not supported in ${badBrowser}`)
  }
})
