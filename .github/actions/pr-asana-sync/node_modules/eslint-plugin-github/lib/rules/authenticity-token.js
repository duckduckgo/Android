module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'disallow usage of CSRF tokens in JavaScript',
      url: require('../url')(module),
    },
    schema: [],
  },

  create(context) {
    function checkAuthenticityTokenUsage(node, str) {
      if (str.includes('authenticity_token')) {
        context.report({
          node,
          message:
            'Form CSRF tokens (authenticity tokens) should not be created in JavaScript and their values should not be used directly for XHR requests.',
        })
      }
    }

    return {
      Literal(node) {
        if (typeof node.value === 'string') {
          checkAuthenticityTokenUsage(node, node.value)
        }
      },
    }
  },
}
