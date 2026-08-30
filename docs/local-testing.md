# Local testing

## Robolectric

Robolectric tests run as JVM unit tests. They do not start an emulator and they
cover app-side Android behavior, such as touch-event routing. They do not verify
Chromium rendering performance or WebView scrolling smoothness; use a physical
device for that.

Use JDK 21, matching CI. On the development machine it is installed through
SDKMAN at `/home/hiosdra/.sdkman/candidates/java/21.0.12-tem`. JDK 26 can make
Robolectric fail before tests run with `Unsupported class file major version 70`.

Run the complete local validation with:

```bash
JAVA_HOME=/home/hiosdra/.sdkman/candidates/java/21.0.12-tem \
ANDROID_HOME=/home/hiosdra/Android/Sdk \
ANDROID_SDK_ROOT=/home/hiosdra/Android/Sdk \
./gradlew lint test assembleDebug --no-daemon --console=plain
```

To run only the article-scroll regressions:

```bash
JAVA_HOME=/home/hiosdra/.sdkman/candidates/java/21.0.12-tem \
ANDROID_HOME=/home/hiosdra/Android/Sdk \
ANDROID_SDK_ROOT=/home/hiosdra/Android/Sdk \
./gradlew :app:testDebugUnitTest \
  --tests com.hiosdra.hreader.presentation.article.ReaderWebViewGestureTest \
  --tests com.hiosdra.hreader.presentation.article.ReaderWebViewScrollTest \
  --tests com.hiosdra.hreader.presentation.article.ArticleScrollProgressTest \
  --no-daemon --console=plain
```

No emulator or connected-device setup is required for these commands. The
Moby-Dick feed in [manual-test-feeds.md](manual-test-feeds.md) is still needed
to evaluate real WebView scrolling on a device.
