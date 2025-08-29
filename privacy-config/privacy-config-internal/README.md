# Internal Privacy Config module
This module is only available in `internal` build variants of the app. It contains the implementation of some utility functions and dev tools to test, modify, and debug the remote privacy config integration.

## Local remote config patches
This module provides options to locally patch the remote config. This is useful for testing purposes, allowing to simulate different configurations without needing to deploy changes to the remote server.

For example, you can use this to ensure a build of your app always has a specific feature flag state, while otherwise using the production remote configuration.

Patching process leverages the [JSON Patch format](https://jsonpatch.com/), allowing you to specify changes in a structured way. The patches are applied at runtime, overriding the actual remote config values.

For example, to ensure a feature flag is always enabled, you can create a patch file like this:

```json
[
  {
    "op": "replace",
    "path": "/features/myFeature/state",
    "value": "enabled"
  }
]
```

Optionally, you can also adjust the remote config version and remove the features hash to ensure the updated version of the remote config is picked up by the app even if the remote config is already cached:

```json
[
  {
    "op": "replace",
    "path": "/features/myFeature/state",
    "value": "enabled"
  },
  {
    "op": "remove",
    "path": "/features/myFeature/hash"
  },
  {
    "op": "replace",
    "path": "/version",
    "value": "999999999999999"
  }
]
```

Make sure to provide a version number higher than the one currently used in production, so the app will recognize it as a new version of the remote config.

### Usage
There are two ways to apply local remote config patches:
1. Using command line parameters when building the app.
2. Defining a path in your `local.properties` file.

Both methods take a list of comma (`,`) separated paths to JSON patch files. The app will apply all patches in the order they are specified.

Note: Regardless of the path where each file is, **each file name needs to be unique**.

#### Command line parameters
You can specify the patches to apply when building the app by using the `-Pconfig_patches` parameter. For example:
```bash
./gradlew installInternalDebug \
-Pconfig_patches=\
privacy-config/privacy-config-internal/local-config-patches/test_patch.json,\
privacy-config/privacy-config-internal/local-config-patches/test_patch2.json
```
This method is useful, for example, for end-to-end tests, where you can specify different patches for different test scenarios and commit them to repository.

#### `local.properties` file
You can also define the patches in a `privacy-config/privacy-config-internal/local.properties` file (create one if it doesn't exist) using the `config_patches` property. For example:
```
config_patches=privacy-config/privacy-config-internal/local-config-patches/test_patch.json,privacy-config/privacy-config-internal/local-config-patches/test_patch2.json
```

This way, you can easily switch between different patch configurations and the modifications will be applied automatically when building the app, even when you build and deploy through Android Studio.

For convenience, the `local.properties` file as well as a `local-config-patches` directory in the `privacy-config-internal` module are ignored by Git, so you can safely use it to store your local patches without affecting the repository.