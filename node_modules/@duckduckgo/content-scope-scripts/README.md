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

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md) for development setup, npm commands, and release process.
