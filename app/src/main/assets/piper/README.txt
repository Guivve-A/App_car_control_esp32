Drop the Piper runtime assets in this folder before running local synthesis:

1. A Spanish voice model, for example:
   - es_ES-carlfm-x_low.onnx
   - es_ES-carlfm-x_low.onnx.json
2. The full espeak-ng-data directory under:
   - assets/piper/espeak-ng-data/

The app expects these default asset paths:
 - piper/es_ES-carlfm-x_low.onnx
 - piper/es_ES-carlfm-x_low.onnx.json
 - piper/espeak-ng-data/

You can change them later from BuildConfig fields in app/build.gradle.kts.
