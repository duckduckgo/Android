# Consumer ProGuard/R8 rules for module: sync-impl
# Applied automatically to release builds via `consumerProguardFiles` in
# gradle/android-library.gradle.
#
# This module (and the app-wide Moshi in app/di/JsonModule.kt, which has NO
# KotlinJsonAdapterFactory) serialises its JSON models with *reflection-based*
# Moshi. Reflection reads fields by name, so R8 must NOT shrink, rename, or
# strip these model classes / their members, and must retain the Signature and
# annotation attributes below. Everything else in the module (ViewModels,
# Activities, repositories, crypto, DI, engine, promotion UI, pixels, ...) is
# intentionally left to R8 for shrinking, optimization, and obfuscation.
#
# IMPORTANT: this is a hand-maintained allowlist. Any NEW class that is
# serialised/deserialised by Moshi in this module (including nested types) MUST
# be added below, or release-build sync will break silently. The robust
# long-term alternative is migrating these models to Moshi codegen
# (@JsonClass(generateAdapter = true)), which removes the need for these rules.
# Validate any change here with a release (R8) build + sync setup/recovery E2E.

# Needed for reflective Moshi: generic element types (e.g. List<Device>) and the
# @field:Json(name = ...) key annotations on fields without a natural JSON name.
-keepattributes Signature, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# --- JSON models in com.duckduckgo.sync.impl (SyncService.kt) ---
-keep class com.duckduckgo.sync.impl.Login { *; }
-keep class com.duckduckgo.sync.impl.Signup { *; }
-keep class com.duckduckgo.sync.impl.Logout { *; }
-keep class com.duckduckgo.sync.impl.ConnectKey { *; }
-keep class com.duckduckgo.sync.impl.EncryptedMessage { *; }
-keep class com.duckduckgo.sync.impl.Connect { *; }
-keep class com.duckduckgo.sync.impl.AccountCreatedResponse { *; }
-keep class com.duckduckgo.sync.impl.LoginResponse { *; }
-keep class com.duckduckgo.sync.impl.DeviceResponse { *; }
-keep class com.duckduckgo.sync.impl.DeviceEntries { *; }
-keep class com.duckduckgo.sync.impl.Device { *; }
-keep class com.duckduckgo.sync.impl.DeviceV2 { *; }
-keep class com.duckduckgo.sync.impl.ErrorResponse { *; }
-keep class com.duckduckgo.sync.impl.TokenRescopeRequest { *; }
-keep class com.duckduckgo.sync.impl.TokenRescopeResponse { *; }
-keep class com.duckduckgo.sync.impl.ProtectedKeysResponse { *; }
-keep class com.duckduckgo.sync.impl.AccessCredentialsResponse { *; }
-keep class com.duckduckgo.sync.impl.AccessCredentialEntry { *; }
-keep class com.duckduckgo.sync.impl.CreateAccessCredentialRequest { *; }
-keep class com.duckduckgo.sync.impl.ProtectedKeyEntry { *; }
-keep class com.duckduckgo.sync.impl.ExchangeEnvelope { *; }
-keep class com.duckduckgo.sync.impl.ExchangeChannelCreateRequest { *; }
-keep class com.duckduckgo.sync.impl.ExchangeMessagesRequest { *; }
-keep class com.duckduckgo.sync.impl.ExchangeMessagesResponse { *; }
-keep class com.duckduckgo.sync.impl.ExchangeMessageEntry { *; }
-keep class com.duckduckgo.sync.impl.RsaJwk { *; }

# --- JSON models in com.duckduckgo.sync.impl (SyncAccountRepository.kt) ---
-keep class com.duckduckgo.sync.impl.LinkCode { *; }
-keep class com.duckduckgo.sync.impl.RecoveryCode { *; }
-keep class com.duckduckgo.sync.impl.ConnectCode { *; }
-keep class com.duckduckgo.sync.impl.ThirdPartyRecoveryCodeWrapper { *; }
-keep class com.duckduckgo.sync.impl.ThirdPartyRecoveryCode { *; }
-keep class com.duckduckgo.sync.impl.InvitationCodeWrapper { *; }
-keep class com.duckduckgo.sync.impl.InvitationCode { *; }
-keep class com.duckduckgo.sync.impl.InvitedDeviceDetails { *; }

# --- JSON model in com.duckduckgo.sync.impl.exchange.v2 ---
-keep class com.duckduckgo.sync.impl.exchange.v2.V2LinkingCodePayload { *; }

# --- JSON model in com.duckduckgo.sync.impl.autorestore ---
-keep class com.duckduckgo.sync.impl.autorestore.RestorePayload { *; }
