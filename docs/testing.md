# Testing

JVM tests cover enrollment policy, serialized persistence, recorder lifecycle, PCM decoding and meeting logic. Public emulator QA installs an x86_64 Debug build, stages synthetic PCM in the app sandbox, and requires `0/3 → 1/3 → 2/3 → 3/3`, `SELF声纹已建立`, screenshots, UI XML and no fatal crash. This validates lifecycle and persistence, not real-device microphone quality.
