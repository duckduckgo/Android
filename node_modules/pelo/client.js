'use strict'

const nanomorph = require('nanomorph')

const createApp = require('./app')

const app = createApp()

const root = document.getElementById('root')
const tree = root.firstElementChild
nanomorph(tree, app.render())
app.onChange(() => {
  nanomorph(tree, app.render())
})
