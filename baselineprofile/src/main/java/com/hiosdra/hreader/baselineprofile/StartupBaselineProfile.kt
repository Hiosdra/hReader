package com.hiosdra.hreader.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

/**
 * Records the classes and methods a cold start touches, so R8 can lay them out for the runtime to
 * load ahead of time. Compose pays for this more than most: without a profile every one of its
 * composition classes is interpreted on the first frame.
 *
 * Run against a connected device or emulator:
 *
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 *
 * The result lands in `app/src/release/generated/baselineProfiles/` and belongs in version control.
 */
class StartupBaselineProfile {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndArticleList() = rule.collect(packageName = "com.hiosdra.hreader") {
        pressHome()
        startActivityAndWait()
        // The article list is what the app opens onto, so let it settle and scroll once: the first
        // scroll is where the list, the row layout and the image loader are all first touched.
        device.waitForIdle()
        device.setOrientationNatural()
    }
}
