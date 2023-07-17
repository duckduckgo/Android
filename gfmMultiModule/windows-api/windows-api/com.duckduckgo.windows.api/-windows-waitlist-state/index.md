//[windows-api](../../../index.md)/[com.duckduckgo.windows.api](../index.md)/[WindowsWaitlistState](index.md)

# WindowsWaitlistState

sealed class [WindowsWaitlistState](index.md)

Public data class for Windows waitlist

#### Inheritors

| |
|---|
| [NotJoinedQueue](-not-joined-queue/index.md) |
| [JoinedWaitlist](-joined-waitlist/index.md) |
| [FeatureEnabled](-feature-enabled/index.md) |
| [InBeta](-in-beta/index.md) |

## Types

| Name | Summary |
|---|---|
| [FeatureEnabled](-feature-enabled/index.md) | [androidJvm]<br>object [FeatureEnabled](-feature-enabled/index.md) : [WindowsWaitlistState](index.md) |
| [InBeta](-in-beta/index.md) | [androidJvm]<br>data class [InBeta](-in-beta/index.md)(val inviteCode: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) : [WindowsWaitlistState](index.md) |
| [JoinedWaitlist](-joined-waitlist/index.md) | [androidJvm]<br>object [JoinedWaitlist](-joined-waitlist/index.md) : [WindowsWaitlistState](index.md) |
| [NotJoinedQueue](-not-joined-queue/index.md) | [androidJvm]<br>object [NotJoinedQueue](-not-joined-queue/index.md) : [WindowsWaitlistState](index.md) |
