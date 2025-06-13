'use strict';

module.exports = (context, badBrowser) => ({
  StaticBlock(node) {
    context.report(
      node,
      `Class Static Blocks are not supported in ${badBrowser}`
    );
  },
});
