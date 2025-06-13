module.exports = {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'enforce a naming convention for js- prefixed classes',
      url: require('../url')(module),
    },
    schema: [],
  },

  create(context) {
    const allJsClassNameRegexp = /\bjs-[_a-zA-Z0-9-]*/g
    const validJsClassNameRegexp = /^js(-[a-z0-9]+)+$/g
    const endWithJsClassNameRegexp = /\bjs-[_a-zA-Z0-9-]*$/g

    function checkStringFormat(node, str) {
      const matches = str.match(allJsClassNameRegexp) || []
      for (const match of matches) {
        if (!match.match(validJsClassNameRegexp)) {
          context.report({node, message: 'js- class names should be lowercase and only contain dashes.'})
        }
      }
    }

    function checkStringEndsWithJSClassName(node, str) {
      if (str.match(endWithJsClassNameRegexp)) {
        context.report({node, message: 'js- class names should be statically defined.'})
      }
    }

    return {
      Literal(node) {
        if (typeof node.value === 'string') {
          checkStringFormat(node, node.value)

          if (
            node.parent &&
            node.parent.type === 'BinaryExpression' &&
            node.parent.operator === '+' &&
            node.parent.left.value
          ) {
            checkStringEndsWithJSClassName(node.parent.left, node.parent.left.value)
          }
        }
      },
      TemplateLiteral(node) {
        for (const quasi of node.quasis) {
          checkStringFormat(quasi, quasi.value.raw)

          if (quasi.tail === false) {
            checkStringEndsWithJSClassName(quasi, quasi.value.raw)
          }
        }
      },
    }
  },
}
