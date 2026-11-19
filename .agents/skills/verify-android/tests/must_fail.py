"""Must FAIL (exit 1), not pass and not error: a wrong expectation and a pixel that never fired are failed checks."""


def run(android):
    android.launch()
    android.mark()
    android.check("the app is on a screen it is not on", android.where() == "NoSuchActivity", android.where())
    android.expect_pixel("m_verify_never_fired")
    android.expect_log("a log line that was never written", r"verify: never logged")
