
const isSiteWithOnlyOwnTrackers = ({ trackersCount, tab }) => {
    if (trackersCount === 0) {
        return false
    }

    for (const companyName of Object.keys(tab.trackers)) {
        if (!isSameEntity(tab.trackers[companyName], tab.parentEntity)) {
            return false
        }
    }

    return true
}

const isSameEntity = (tracker, parentEntity) => {
    const parent = parentEntity?.displayName || null

    return parent === tracker.displayName
}

const offset = 'a'.charCodeAt(0)
const colorCount = 16
function getColorId (value) {
    const characters = value.toLowerCase().split('')
    const sum = characters.reduce((total, character) => total + character.charCodeAt(0) - offset, 0)
    return sum % colorCount + 1
}

module.exports = {
    isSiteWithOnlyOwnTrackers: isSiteWithOnlyOwnTrackers,
    isSameEntity: isSameEntity,
    getColorId: getColorId
}
