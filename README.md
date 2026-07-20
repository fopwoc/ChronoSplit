# ChronoSplit

ChronoSplit is an iOS-first, Kotlin Multiplatform run timer with optional OBS
integration. The mobile app owns configuration, active timing state, and attempt
history. Desktop and Docker relays are replaceable, non-durable projections of
the state published by mobile.

LiveSplit configurations compatible. Ready for `.lss` and `.ls1l` files.


![img.png](.github/assets/img.png)

## Modules

- `shared/models`: common wire and timer models.
- `shared/compose`: reusable Compose remote-display UI.
- `shared/server`: common Ktor relay protocol and state fan-out.
- `app/appShared`: common mobile domain, session logic, and Room 3 history.
- `app/androidApp`: Android Material 3 Expressive client with Navigation 3.
- `app/iosApp`: an iOS KMP bridge with Darwin networking; the native SwiftUI app
  is a thin UI wrapper over it.
- `app/jvmApp`: desktop relay display plus an embedded Ktor relay.
- `app/webApp`: WebAssembly relay display.
- `backend`: the standalone KMP/JVM relay that serves the web build.
