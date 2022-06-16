/* global contentScopeFeatures */

import { processConfig } from './../src/apple-utils'

function init () {
    const processedConfig = processConfig($CONTENT_SCOPE$, $USER_UNPROTECTED_DOMAINS$, $USER_PREFERENCES$)
    if (processedConfig.site.allowlisted) {
        return
    }

    contentScopeFeatures.load()

    contentScopeFeatures.init(processedConfig)

    // Not supported:
    // contentScopeFeatures.update(message)
}

init()
