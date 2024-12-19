"use strict";
(() => {
  // pages/duckplayer/src/inline.js
  var param = new URLSearchParams(window.location.search).get("platform");
  if (isAllowed(param)) {
    document.documentElement.dataset.platform = String(param);
  } else {
    document.documentElement.dataset.platform = "android";
  }
  function isAllowed(input) {
    const allowed = ["windows", "apple", "integration"];
    return allowed.includes(input);
  }
})();
