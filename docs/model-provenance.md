# Model Provenance

`MODEL_MANIFEST.json` locks every downloaded artifact by fixed HTTPS URL, exact byte count, and SHA-256. CI downloads models only into its ephemeral workspace. Model and AAR binaries are excluded from Git history.
