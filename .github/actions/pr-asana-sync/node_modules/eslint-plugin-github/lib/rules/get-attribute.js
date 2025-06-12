const svgElementAttributes = require('svg-element-attributes')

const attributeCalls = /^(get|has|set|remove)Attribute$/
const validAttributeName = /^[a-z][a-z0-9-]*$/

// these are common SVG attributes that *must* have the correct case to work
const camelCaseAttributes = Object.values(svgElementAttributes)
  .reduce((all, elementAttrs) => all.concat(elementAttrs), [])
  .filter(name => !validAttributeName.test(name))

const validSVGAttributeSet = new Set(camelCaseAttributes)

// lowercase variants of camelCase SVG attributes are probably an error
const invalidSVGAttributeSet = new Set(camelCaseAttributes.map(name => name.toLowerCase()))

function isValidAttribute(name) {
  return validSVGAttributeSet.has(name) || (validAttributeName.test(name) && !invalidSVGAttributeSet.has(name))
}

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'disallow wrong usage of attribute names',
      url: require('../url')(module),
    },
    fixable: 'code',
    schema: [],
  },
  create(context) {
    return {
      CallExpression(node) {
        if (!node.callee.property) return

        const calleeName = node.callee.property.name
        if (!attributeCalls.test(calleeName)) return

        const attributeNameNode = node.arguments[0]
        if (!attributeNameNode) return

        if (!isValidAttribute(attributeNameNode.value)) {
          context.report({
            node: attributeNameNode,
            message: 'Attributes should be lowercase and hyphen separated, or part of the SVG whitelist.',
            fix(fixer) {
              return fixer.replaceText(attributeNameNode, `'${attributeNameNode.value.toLowerCase()}'`)
            },
          })
        }
      },
    }
  },
}
