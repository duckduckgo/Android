//[browser-api](../../index.md)/[com.duckduckgo.app.trackerdetection.model](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [Entity](-entity/index.md) | [androidJvm]<br>interface [Entity](-entity/index.md) |
| [TrackerStatus](-tracker-status/index.md) | [androidJvm]<br>enum [TrackerStatus](-tracker-status/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[TrackerStatus](-tracker-status/index.md)&gt; |
| [TrackerType](-tracker-type/index.md) | [androidJvm]<br>enum [TrackerType](-tracker-type/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[TrackerType](-tracker-type/index.md)&gt; |
| [TrackingEvent](-tracking-event/index.md) | [androidJvm]<br>data class [TrackingEvent](-tracking-event/index.md)(val documentUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val trackerUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val categories: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;?, val entity: [Entity](-entity/index.md)?, val surrogateId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, val status: [TrackerStatus](-tracker-status/index.md), val type: [TrackerType](-tracker-type/index.md)) |
