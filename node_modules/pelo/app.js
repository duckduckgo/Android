'use strict'

module.exports = function () {
  // Allow `bel` to be swapped in benchmarking
  const html = require('bel')

  const greeting = 'Hello'
  const name = 'special characters, <, >, &'
  const drinks = [
      { name: 'Cafe Latte', price: 3.0, sold: false },
      { name: 'Cappucino', price: 2.9, sold: true },
      { name: 'Club Mate', price: 2.2, sold: true },
      { name: 'Berliner Weiße', price: 3.5, sold: false }
  ]

  const listeners = []
  function onChange (listener) {
    listeners.push(listener)
  }
  function notifyChange () {
    listeners.forEach((listener) => listener())
  }

  function deleteDrink (drink) {
    const index = drinks.indexOf(drink)
    if (index >= 0) {
      drinks.splice(index, 1)
    }
    notifyChange()
  }

  function drinkView (drink, deleteDrink) {
    return html`
      <li>
        ${drink.name} is € ${drink.price}
        <button ${{type: 'submit', 'data-ga-btn': 'Button'}} onclick=${() => deleteDrink(drink)} disabled="${!drink.sold}">Give me!</button>
      </li>
    `
  }

  function mainView (greeting, name, drinks, deleteDrink) {
    return html`
      <div>
        <p>${greeting}, ${name}!</p>
        ${drinks.length > 0 ? html`
          <ul>
            ${drinks.map(drink => drinkView(drink, deleteDrink))}
          </ul>
        ` : html`
          <p>All drinks are gone!</p>
        `}
        <p>
          attributes: <input type=text value=${''} disabled onclick="${() => alert('hello')}" ${{ title: '<Special " characters>' }} />
        </p>
      </div>
    `
  }

  function render () {
    return mainView(greeting, name, drinks, deleteDrink)
  }

  return {
    render: render,
    onChange: onChange
  }
}
