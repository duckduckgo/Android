//[browser-api](../../../index.md)/[com.duckduckgo.browser.api](../index.md)/[BrowserLifecycleObserver](index.md)/[onClose](on-close.md)

# onClose

[androidJvm]\
open fun [onClose](on-close.md)()

Called when the application is closed. Close means that the application does not have any activity in STARTED state, however it may have activities in CREATED state. Examples are:

- 
   when user homes the app
- 
   when user swipes-closes the app

see also [BrowserLifecycleObserver.onExit](on-exit.md)
