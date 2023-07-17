//[browser-api](../../../index.md)/[com.duckduckgo.browser.api](../index.md)/[BrowserLifecycleObserver](index.md)

# BrowserLifecycleObserver

[androidJvm]\
interface [BrowserLifecycleObserver](index.md)

Implement this interface and contribute it as a multibinding if you want to get callbacks about the lifecycle of the DDG Browser application.

## Functions

| Name | Summary |
|---|---|
| [onBackground](on-background.md) | [androidJvm]<br>open fun [onBackground](on-background.md)()<br>Called every time the application is backgrounded |
| [onClose](on-close.md) | [androidJvm]<br>open fun [onClose](on-close.md)()<br>Called when the application is closed. Close means that the application does not have any activity in STARTED state, however it may have activities in CREATED state. Examples are: |
| [onExit](on-exit.md) | [androidJvm]<br>open fun [onExit](on-exit.md)()<br>Called when the application exits. Exit means that the application does NOT have any activity in CREATED state. This call will always follow the [BrowserLifecycleObserver.onClose](on-close.md) call |
| [onForeground](on-foreground.md) | [androidJvm]<br>open fun [onForeground](on-foreground.md)()<br>Called every time the application is foregrounded |
| [onOpen](on-open.md) | [androidJvm]<br>open fun [onOpen](on-open.md)(isFreshLaunch: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html))<br>Called once when the application is opened. [isFreshLaunch](on-open.md) will be `true` if it is a fresh launch, `false` otherwise |
