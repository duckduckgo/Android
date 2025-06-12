const {elementType, getProp, getLiteralPropValue} = require('jsx-ast-utils')

/*
Allows custom component to be mapped to an element type.
When a default is set, all instances of the component will be mapped to the default.
If a prop determines the type, it can be specified with `props`.

For now, we only support the mapping of one prop type to an element type, rather than combinations of props.
*/
function getElementType(context, node, lazyElementCheck = false) {
  const {settings} = context

  if (lazyElementCheck) {
    return elementType(node)
  }

  // check if the node contains a polymorphic prop
  const polymorphicPropName = settings?.github?.polymorphicPropName ?? 'as'
  const rawElement = getLiteralPropValue(getProp(node.attributes, polymorphicPropName)) ?? elementType(node)

  // if a component configuration does not exists, return the raw element
  if (!settings?.github?.components?.[rawElement]) return rawElement

  const defaultComponent = settings.github.components[rawElement]

  // check if the default component is also defined in the configuration
  return defaultComponent ? defaultComponent : defaultComponent
}

module.exports = {getElementType}
