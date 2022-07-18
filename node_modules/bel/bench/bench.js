var hrtime = require('browser-process-hrtime')

module.exports = function (name, fn, times) {
  times = times || 10000
  var start = hrtime()
  for (var i = 0; i < times; i++) {
    fn()
  }
  var end = hrtime(start)
  console.log((end[1] / 1000000) / times, 'ms', name)
}
