# Project Overview

A collection of shared JavaScript and HTML projects that are used within our Browsers.

## Child Projects

### [Injected](./injected)

A library of features/protections that are executed within our browsers. 

Features have a deep integration with [privacy-configuration](https://github.com/duckduckgo/privacy-configuration),
to allow dynamic enabling or disabling of features at runtime.

### [Special Pages](./special-pages)

This project contains a set of isolated JavaScript applications that end up being embedded directly into
our browsers. A 'special page' can be as simple as a single-screen, or as complex as a New Tab Page.

### [Messaging](./messaging)

This project serves as an abstraction layer for seamless web-to-native and native-to-web
communications, inspired by the [JSON-RPC](https://www.jsonrpc.org/specification) format.

The module provides three core methods: `notify` for fire-and-forget messages, `request` for asynchronous request-response
interactions, and `subscribe` for handling push-based data updates.

### [Types-generator](./types-generator)

Utilities to automatically generate TypeScript types from JSON Schema files.

---

## NPM commands

Consider using [nvm](https://github.com/nvm-sh/nvm) to manage node versions, after installing in the project directory run:

```
nvm use
```

From the top-level root folder of this npm workspace, you can run the following npm commands:

**Install dependencies**:

Will install all the dependencies we need to build and run the project:
```
npm install
```

**Build all workspaces**:

Use this to produce the same output as a release. The `build` directory will be populated with
various artifacts.

```sh
npm run build
```

> [!TIP]
> You can run the `build` command from within any sub-project too, the artifacts will always be
> lifted out to the root-level `build` folder.

**Run unit tests for all workspaces**:

```sh
npm run test-unit
```

**Run integration tests for all workspaces**:
```sh
npm run test-int
```

**Run extended integration tests for all workspaces**:
```sh
npm run test-int-x
```

**Clean tree and check for changes**:
```sh
npm run test-clean-tree
```

**Generate documentation using TypeDoc**:
```sh
npm run docs
```

**Generate and watch documentation using TypeDoc**:
```sh
npm run docs-watch
```

**Compile TypeScript files**:
```sh
npm run tsc
```

**Watch and compile TypeScript files**:
```sh
npm run tsc-watch
```

**Lint the codebase using ESLint**:
```sh
npm run lint
```

**Lint and automatically fix issues**:
```sh
npm run lint-fix
```

**Serve integration test pages on port 3220**:
```sh
npm run serve
```

**Serve special pages on port 3221**:
```sh
npm run serve-special-pages
```
