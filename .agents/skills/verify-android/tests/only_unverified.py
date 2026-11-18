"""Must not PASS: a proof that only records what it couldn't verify has proven nothing, so its verdict is FAIL."""


def run(android):
    android.launch()
    android.unverified("the thing under test", "the selftest checks that this alone isn't a pass")
