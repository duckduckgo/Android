'use strict';

module.exports = (context, badBrowser) => ({
  ':function[async=true][generator=true]'(node) {
    context.report(node, `Async Generators are not supported in ${badBrowser}`)
  }
})
