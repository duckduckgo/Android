'use strict';

const functionTypes = new Set([
  'FunctionDeclaration',
  'FunctionExpression',
  'ArrowFunctionExpression',
]);

module.exports = (context, badBrowser) => ({
  AwaitExpression(node) {
    let currentNode = node;
    while (currentNode.parent) {
      currentNode = currentNode.parent;
      if (functionTypes.has(currentNode.type) && currentNode.async) {
        return;
      }
    }
    context.report(node, `Top-level await is not supported in ${badBrowser}`)
  },
})
