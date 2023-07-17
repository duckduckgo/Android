//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.service](../index.md)/[VpnSocketProtector](index.md)/[protect](protect.md)

# protect

[androidJvm]\
abstract fun [protect](protect.md)(socket: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)

Call this method to protect the socket from VPN.

#### Parameters

androidJvm

| | |
|---|---|
| socket | The file descriptor of the socket to protect. @#return true if the socket is protected, false otherwise. |
