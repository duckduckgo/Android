"use strict";

module.exports = {
    rules: {
        "match-regex": require("./lib/rules/match-regex"),
        "match-exported": require("./lib/rules/match-exported"),
        "no-index": require("./lib/rules/no-index")
    }
};
