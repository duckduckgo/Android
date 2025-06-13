'use strict';

module.exports = (context, badBrowser) => ({
  ClassPrivateProperty(node) {
    context.report(node, `Private Class Fields are not supported in ${badBrowser}`)
  },
  PrivateIdentifier(node) {
    context.report(node, `Private Class Fields are not supported in ${badBrowser}`)
  }
})
