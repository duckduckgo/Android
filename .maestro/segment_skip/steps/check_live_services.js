// Fails fast with a named cause when a live dependency, rather than the YouTube player, would make the
// UI assertion fail. Inputs: VIDEO_ID, HASH_PREFIX (first 4 hex chars of sha256(VIDEO_ID)) and
// SEGMENT_START (seconds). The dataset URL mirrors SegmentSkipDatasetService in ad-blocking-impl.
const base = 'https://protection.duckduckgo.com/api/protection/segmentSkip/v1/android'

const revision = http.get(`${base}/revision`)
if (!revision.ok) {
    throw new Error(`DATASET SERVICE FAILURE: revision endpoint returned HTTP ${revision.status}`)
}
// The latest revision's diff keeps this small; the app's first download asks for the full snapshot.
const latest = json(revision.body).revision
const prefixes = http.get(`${base}/hashPrefix?revision=${latest}&format=binary&version=1`)
if (!prefixes.ok) {
    throw new Error(`DATASET SERVICE FAILURE: hashPrefix endpoint returned HTTP ${prefixes.status}`)
}
// SegmentSkipBinaryStore rejects any file without this magic, so the app never gets a dataset.
if (!prefixes.body.startsWith('SBPL')) {
    throw new Error('DATASET FORMAT FAILURE: hashPrefix did not return the binary format the app expects')
}

const matches = http.get(`${base}/matches?hashPrefix=${HASH_PREFIX}`)
if (!matches.ok) {
    throw new Error(`SEGMENT LOOKUP SERVICE FAILURE: matches endpoint returned HTTP ${matches.status}`)
}
const video = json(matches.body).find((v) => v.videoID === VIDEO_ID)
const start = Number(SEGMENT_START)
const hasSegment = (video?.segments ?? []).some(
    (s) => s.category === 'sponsor' && s.actionType === 'skip' && Math.abs(s.segment[0] - start) <= 2 && s.segment[1] - s.segment[0] >= 30,
)
if (!hasSegment) {
    throw new Error(`MISSING SEGMENT DATA: ${VIDEO_ID} no longer has a sponsor segment starting near ${start}s and lasting 30s or more`)
}
