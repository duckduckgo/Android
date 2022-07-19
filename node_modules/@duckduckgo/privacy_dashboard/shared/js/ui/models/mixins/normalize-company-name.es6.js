module.exports = {
    normalizeCompanyName (companyName) {
        return (companyName || '')
            .toLowerCase()
            // Remove TLD suffixes
            // e.g. Fixes cases like "amazon.com" -> "amazon"
            .replace(/\.[a-z]+$/, '')
            // Remove non-alphanumeric characters
            // e.g. Fixes cases like "new relic" -> "newrelic"
            .replace(/[^a-z0-9]/g, '')
    }
}
