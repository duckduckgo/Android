'use strict';

module.exports = (context, badBrowser) => ({
  // Ignore type annotations that don't assign
  'ClassProperty[computed=true]:not([typeAnnotation]:not([value]))'(node) {
    context.report(node, `Computed Class Fields are not supported in ${badBrowser}`)
  },
  'PropertyDefinition[computed=true]:not([typeAnnotation]:not([value]))'(node) {
    context.report(node, `Computed Class Fields are not supported in ${badBrowser}`)
  }
})
