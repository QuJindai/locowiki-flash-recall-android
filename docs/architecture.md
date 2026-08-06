# Architecture

The application is a local-first Kotlin/Compose Android app. Meeting ASR, voiceprint extraction, Room persistence, and fact recall run on device. SELF enrollment is available before meetings and uses three independent segments. Production segments each own a fresh `AudioRecord`; Debug emulator QA may use bounded PCM files inside the app sandbox.
