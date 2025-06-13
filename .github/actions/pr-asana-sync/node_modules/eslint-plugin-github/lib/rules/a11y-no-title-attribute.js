const {getProp, getPropValue} = require('jsx-ast-utils')
const {getElementType} = require('../utils/get-element-type')

const SEMANTIC_ELEMENTS = [
  'a',
  'button',
  'summary',
  'select',
  'option',
  'textarea',
  'input',
  'span',
  'div',
  'p',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'details',
  'summary',
  'dialog',
  'tr',
  'th',
  'td',
  'label',
]

const ifSemanticElement = (context, node) => {
  const elementType = getElementType(context, node.openingElement, true)

  for (const semanticElement of SEMANTIC_ELEMENTS) {
    if (elementType === semanticElement) {
      return true
    }
  }
  return false
}

module.exports = {
  meta: {
    docs: {
      description: 'Guards against developers using the title attribute',
      url: require('../url')(module),
    },
    schema: [],
  },

  create(context) {
    return {
      JSXElement: node => {
        const elementType = getElementType(context, node.openingElement, true)
        if (elementType !== `iframe` && ifSemanticElement(context, node)) {
          const titleProp = getPropValue(getProp(node.openingElement.attributes, `title`))
          if (titleProp) {
            context.report({
              node,
              message: 'The title attribute is not accessible and should never be used unless for an `<iframe>`.',
            })
          }
        }
      },
    }
  },
}
