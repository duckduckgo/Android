'use strict';

module.exports = (context, badBrowser) => ({
  'CatchClause:not([param])'(node) {
    context.report(node, `Optional Catch Parameters are not supported in ${badBrowser}`)
  }
})
