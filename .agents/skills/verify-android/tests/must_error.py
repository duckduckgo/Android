"""Must ERROR (exit 2): a driving step that can't be done stops the run, and the check after it is never recorded."""


def run(android):
    android.launch()
    android.tap("text:verify: never on screen")
    android.check("never reached", True)
