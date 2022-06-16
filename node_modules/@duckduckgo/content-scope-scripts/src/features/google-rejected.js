export function init () {
    try {
        if ('browsingTopics' in Document.prototype) {
            delete Document.prototype.browsingTopics
        }
        if ('joinAdInterestGroup' in Navigator.prototype) {
            delete Navigator.prototype.joinAdInterestGroup
        }
        if ('leaveAdInterestGroup' in Navigator.prototype) {
            delete Navigator.prototype.leaveAdInterestGroup
        }
        if ('updateAdInterestGroups' in Navigator.prototype) {
            delete Navigator.prototype.updateAdInterestGroups
        }
        if ('runAdAuction' in Navigator.prototype) {
            delete Navigator.prototype.runAdAuction
        }
        if ('adAuctionComponents' in Navigator.prototype) {
            delete Navigator.prototype.adAuctionComponents
        }
    } catch {
        // Throw away this exception, it's likely a confict with another extension
    }
}
