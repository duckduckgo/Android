/**
 * @fileoverview Rule to flag use of .only in tests, preventing focused tests being committed accidentally
 * @author Levi Buzolic
 */

/** @typedef {{block?: string[], focus?: string[], functions?: string[], fix?: boolean}} Options */

/** @type {Options} */
const defaultOptions = {
	block: [
		"describe",
		"it",
		"context",
		"test",
		"tape",
		"fixture",
		"serial",
		"Feature",
		"Scenario",
		"Given",
		"And",
		"When",
		"Then",
	],
	focus: ["only"],
	functions: [],
	fix: false,
};

/** @type {import('eslint').Rule.RuleModule} */
module.exports = {
	meta: {
		docs: {
			description: "disallow .only blocks in tests",
			category: "Possible Errors",
			recommended: true,
			url: "https://github.com/levibuzolic/eslint-plugin-no-only-tests",
		},
		fixable: "code",
		schema: [
			{
				type: "object",
				properties: {
					block: {
						type: "array",
						items: {
							type: "string",
						},
						uniqueItems: true,
						default: defaultOptions.block,
					},
					focus: {
						type: "array",
						items: {
							type: "string",
						},
						uniqueItems: true,
						default: defaultOptions.focus,
					},
					functions: {
						type: "array",
						items: {
							type: "string",
						},
						uniqueItems: true,
						default: defaultOptions.functions,
					},
					fix: {
						type: "boolean",
						default: defaultOptions.fix,
					},
				},
				additionalProperties: false,
			},
		],
	},
	create(context) {
		/** @type {Options} */
		const options = Object.assign({}, defaultOptions, context.options[0]);
		const blocks = options.block || [];
		const focus = options.focus || [];
		const functions = options.functions || [];
		const fix = !!options.fix;

		return {
			Identifier(node) {
				if (functions.length && functions.indexOf(node.name) > -1) {
					context.report({
						node,
						message: `${node.name} not permitted`,
					});
				}

				const parentObject =
					"object" in node.parent ? node.parent.object : undefined;
				if (parentObject == null) return;
				if (focus.indexOf(node.name) === -1) return;

				const callPath = getCallPath(node.parent).join(".");

				// comparison guarantees that matching is done with the beginning of call path
				if (
					blocks.find((block) => {
						// Allow wildcard tail matching of blocks when ending in a `*`
						if (block.endsWith("*"))
							return callPath.startsWith(block.replace(/\*$/, ""));
						return callPath.startsWith(`${block}.`);
					})
				) {
					const rangeStart = node.range?.[0];
					const rangeEnd = node.range?.[1];

					context.report({
						node,
						message: `${callPath} not permitted`,
						fix:
							fix && rangeStart != null && rangeEnd != null
								? (fixer) => fixer.removeRange([rangeStart - 1, rangeEnd])
								: undefined,
					});
				}
			},
		};
	},
};

/**
 *
 * @param {import('estree').Node} node
 * @param {string[]} path
 * @returns
 */
function getCallPath(node, path = []) {
	if (node) {
		const nodeName =
			"name" in node && node.name
				? node.name
				: "property" in node && node.property && "name" in node.property
					? node.property?.name
					: undefined;
		if ("object" in node && node.object && nodeName)
			return getCallPath(node.object, [nodeName, ...path]);
		if ("callee" in node && node.callee) return getCallPath(node.callee, path);
		if (nodeName) return [nodeName, ...path];
		return path;
	}
	return path;
}
