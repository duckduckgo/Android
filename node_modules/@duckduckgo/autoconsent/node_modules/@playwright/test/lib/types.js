"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});

var _test = require("../types/test");

Object.keys(_test).forEach(function (key) {
  if (key === "default" || key === "__esModule") return;
  if (key in exports && exports[key] === _test[key]) return;
  Object.defineProperty(exports, key, {
    enumerable: true,
    get: function () {
      return _test[key];
    }
  });
});