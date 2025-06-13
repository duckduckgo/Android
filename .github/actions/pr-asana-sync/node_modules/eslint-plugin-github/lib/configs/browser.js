module.exports = {
  env: {
    browser: true,
  },
  plugins: ['github', 'escompat'],
  extends: ['plugin:escompat/recommended'],
  rules: {
    'escompat/no-dynamic-imports': 'off',
    'github/async-currenttarget': 'error',
    'github/async-preventdefault': 'error',
    'github/get-attribute': 'error',
    'github/no-blur': 'error',
    'github/no-dataset': 'error',
    'github/no-innerText': 'error',
    'github/no-inner-html': 'error',
    'github/unescaped-html-literal': 'error',
    'github/no-useless-passive': 'error',
    'github/require-passive-events': 'error',
    'github/prefer-observers': 'error',
    'import/no-nodejs-modules': 'error',
    'no-restricted-syntax': [
      'error',
      {
        selector: "NewExpression[callee.name='URL'][arguments.length=1]",
        message: 'Please pass in `window.location.origin` as the 2nd argument to `new URL()`',
      },
    ],
  },
}
