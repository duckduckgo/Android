'use strict';

const path = require("path");
const browserslist = require("browserslist");
const { version, homepage } = require("../package.json");
const flatTypeScriptConfigs = {};
const createRule = (name, browserstring, description, { ts = null } = {}) => {
  const rule = require(`./rules/${name}`);
  module.exports.rules[name] = {
    meta: Object.assign(
      {
        type: "problem",
        docs: {
          description,
          recommended: true,
          url: `${homepage}/blob/v${version}/docs/${name}.md`,
        },
        fixable: false,
        schema: [],
        deprecated: false,
        replacedBy: null,
      },
      rule.meta || {}
    ),
    create(context) {
      let browsers = browserslist(browserstring);
      const config = browserslist.findConfig(path.dirname(
        context.filename ?? context.getFilename()
      )) || {
        defaults: "defaults",
      };
      const desiredBrowsers = browserslist(config.defaults);
      const badBrowsers = desiredBrowsers
        .filter((browser) => browsers.includes(browser))
        .join(", ");
      if (globalThis.ESLINT_TESTING || badBrowsers) {
        const create = typeof rule === "function" ? rule : rule.create;
        return create(context, globalThis.ESLINT_TESTING ? undefined : badBrowsers);
      }
      return {};
    },
  };

  const configName = `typescript-${ts || "base"}`;
  const flatConfigName = `flat/${configName}`;
  if (!module.exports.configs[configName]) {
    flatTypeScriptConfigs[configName] = [{
      name: `escompat/${configName}`,
      plugins: {
        escompat: module.exports
      },
      rules: {}
    }];
    const config = { rules: {} };
    if (ts === 2016) {
      config.extends = [`plugin:escompat/typescript-base`];
      flatTypeScriptConfigs[configName].unshift(
        ...flatTypeScriptConfigs['typescript-base']
      );
    } else if (ts) {
      let previous = ts - 1;
      while (!module.exports.configs[`typescript-${previous}`]) previous -= 1;

      config.extends = [`plugin:escompat/typescript-${previous}`];
      flatTypeScriptConfigs[configName].unshift(
        ...flatTypeScriptConfigs[`typescript-${previous}`]
      );
    }
    module.exports.configs[configName] = config;
    module.exports.configs[flatConfigName] = flatTypeScriptConfigs[configName];
  }
  module.exports.configs[`typescript-base`].rules[`escompat/${name}`] = "off";
  module.exports.configs[`flat/typescript-base`].at(-1).rules[`escompat/${name}`] = "off";
  module.exports.configs[configName].rules[`escompat/${name}`] = "error";
  module.exports.configs[flatConfigName].at(-1).rules[`escompat/${name}`] = "error";
};

module.exports = { rules: {}, configs: {} };
// ES2015
createRule(
  "no-edge-destructure-bug",
  "edge < 18",
  "disallow the use of specific destructuring patterns that cause bugs in old Edge"
);

// ES2016
createRule(
  "no-exponentiation-operator",
  "chrome < 52, edge < 14, firefox < 52, safari < 10.1",
  "disallow use of exponentiation operator (**)",
  { ts: 2016 }
);

// ES2018
createRule(
  "no-async-iteration",
  "edge < 79, safari < 12, firefox < 57, chrome < 63",
  "disallow the use of `for await of` style loops",
  { ts: 2018 }
);
createRule(
  "no-async-generator",
  "edge < 79, safari < 12, firefox < 57, chrome < 63",
  "disallow the use of async generator functions",
  { ts: 2018 }
);
createRule(
  "no-object-rest-spread",
  "edge < 79, safari < 11.1, firefox < 55, chrome < 60",
  "disallow object rest/spread patterns",
  { ts: 2018 }
);
createRule(
  "no-regexp-s-flag",
  "edge < 79, safari < 11.1, firefox < 78, chrome < 62",
  "disallow the use of the RegExp `s` flag"
);
createRule(
  "no-regexp-lookbehind",
  "edge < 79, safari < 16.4, firefox < 78, chrome < 62",
  "disallow the use of RegExp lookbehinds"
);
createRule(
  "no-regexp-named-group",
  "edge < 79, safari 11.1, firefox < 78, chrome < 64",
  "disallow the use of RegExp named groups"
);

// ES2019
createRule(
  "no-optional-catch",
  "edge < 79, safari < 11.1, firefox < 58, chrome < 66",
  "always require catch() to have an argument",
  { ts: 2019 }
);

// ES2020
createRule(
  "no-dynamic-imports",
  "edge < 79, safari < 11, firefox < 67, chrome < 63",
  "disallow dynamic import statements"
);
createRule(
  "no-optional-chaining",
  "edge < 80, safari < 13.1, firefox < 72, chrome < 80",
  "disallow the .? optional chaining operator",
  { ts: 2020 }
);
createRule(
  "no-nullish-coalescing",
  "edge < 80, safari < 13.1, firefox < 72, chrome < 80",
  "disallow the ?? nullish coalescing operator",
  { ts: 2020 }
);
createRule(
  "no-bigint",
  "edge < 79, safari < 14, firefox < 68, chrome < 67",
  "disallow bigints"
);

// ES2021
createRule(
  "no-numeric-separators",
  "edge < 79, safari < 13, firefox < 68, chrome < 75",
  "disallow use of numeric separators like 1_000_000",
  { ts: 2021 }
);

createRule(
  "no-logical-assignment-operator",
  "edge < 85, safari < 14, firefox < 79, chrome < 85",
  "disallow logical assignment operators like &&=",
  { ts: 2021 }
);

// ES2022
createRule(
  "no-public-static-class-fields",
  "edge < 79, safari < 14.5, firefox < 75, chrome < 72",
  "disallow public static class fields like foo = 1",
  { ts: 2022 }
);
createRule(
  "no-public-instance-class-fields",
  "edge < 79, safari < 14.5, firefox < 69, chrome < 72",
  "disallow public class fields like foo = 1",
  { ts: 2022 }
);
createRule(
  "no-computed-public-class-fields",
  "edge < 79, safari < 14.5, firefox < 69, chrome < 74",
  "disallow computed public static or instance class fields like [foo] = 1",
  { ts: 2022 }
);
createRule(
  "no-private-class-fields",
  "edge < 79, safari < 14.5, firefox < 90, chrome < 74",
  "disallow private class fields like #foo = 1",
  { ts: 2022 }
);
createRule(
  "no-class-static-blocks",
  "edge < 94, safari < 16.4, firefox < 93, chrome < 94",
  "disallow static blocks like `static { x = 1 }`",
  { ts: 2022 }
);
createRule(
  "no-top-level-await",
  "edge < 89, safari < 15, firefox < 89, chrome < 89",
  "disallow await keyword outside of async function context",
  { ts: 2022 }
);

// ES2023
createRule(
  "no-hashbang-comment",
  "edge < 79, safari < 13.1, firefox < 67, chrome < 74",
  "disallow hashbang comments",
  { ts: 2023 }
);

// ES2024
createRule(
  "no-regexp-v-flag",
  "edge > 0, safari < 17, firefox < 116, chrome < 112",
  "disallow the use of the RegExp `v` flag",
  { ts: 2024 }
);

// ES2025
createRule(
  "no-regexp-duplicate-named-groups",
  "edge < 125, safari < 17, firefox < 129, chrome < 125",
  "disallow the use of RegExp duplicate named groups",
  { ts: 2025 }
);

// Proposals...
createRule(
  "no-do-expression",
  "edge > 0, safari > 0, firefox > 0, chrome > 0",
  'disallow "do" expressions'
);
createRule(
  "no-bind-operator",
  "edge > 0, safari > 0, firefox > 0, chrome > 0",
  "disallow the :: bind operator"
);
createRule(
  "no-pipeline-operator",
  "edge > 0, safari > 0, firefox > 0, chrome > 0",
  "disallow the > pipeline operator"
);

module.exports.configs.recommended = {
  plugins: ["escompat"],
  parserOptions: { ecmaVersion: 2025 },
  rules: Object.keys(module.exports.rules).reduce(
    (o, r) => ((o["escompat/" + r] = ["error"]), o),
    {}
  ),
};

module.exports.configs["flat/recommended"] = {
  name: 'escompat/flat/recommended',
  plugins: {
    escompat: module.exports
  },
  languageOptions: {
    ecmaVersion: 2025
  },
  rules: Object.keys(module.exports.rules).reduce(
    (o, r) => ((o["escompat/" + r] = ["error"]), o),
    {}
  ),
};

module.exports.configs.typescript = {
  extends: ["plugin:escompat/typescript-2025"],
};
module.exports.configs['flat/typescript'] = flatTypeScriptConfigs[
  'typescript-2025'
];

if (require.main === module) {
  console.log(require("util").inspect(module.exports, { depth: Infinity }));
}

