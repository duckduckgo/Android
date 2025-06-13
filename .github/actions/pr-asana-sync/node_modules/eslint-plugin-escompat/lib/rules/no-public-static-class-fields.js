'use strict';

module.exports = (context, badBrowser) => ({
  // Ignore type annotations that don't assign
  'ClassProperty[static=true]:not([typeAnnotation]:not([value]))'(node) {
    context.report(node, `Static Class Fields are not supported in ${badBrowser}`)
  },
  'PropertyDefinition[static=true]:not([typeAnnotation]:not([value]))'(node) {
    context.report(node, `Static Class Fields are not supported in ${badBrowser}`)
  }
})
