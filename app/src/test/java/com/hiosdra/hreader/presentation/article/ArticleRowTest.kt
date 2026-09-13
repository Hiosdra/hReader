package com.hiosdra.hreader.presentation.article

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import coil3.ImageLoader
import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import com.hiosdra.hreader.presentation.theme.HReaderTheme
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ArticleRowTestApplication::class, sdk = [35])
class ArticleRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `immediate read transition keeps the read state announcement`() {
        setContent(isRead = true)
        val context = RuntimeEnvironment.getApplication()

        composeTestRule.onNode(
            hasStateDescription(context.getString(R.string.article_read))
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.article_mark_as_unread)
        ).assertIsDisplayed()
    }

    @Test
    fun `immediate unread transition keeps the unread action announcement`() {
        setContent(isRead = false)
        val context = RuntimeEnvironment.getApplication()

        composeTestRule.onNode(
            hasStateDescription(context.getString(R.string.article_unread))
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.article_mark_as_read)
        ).assertIsDisplayed()
    }

    private fun setContent(isRead: Boolean) {
        val context = RuntimeEnvironment.getApplication()
        composeTestRule.setContent {
            HReaderTheme {
                ArticleRow(
                    entry = ArticleRowModel(
                        id = 1L,
                        title = "Article title",
                        preview = null,
                        feedTitle = "Feed",
                        publishedTime = "12:00",
                        imageUrl = null,
                        isRead = isRead
                    ),
                    onOpen = {},
                    onCheckedChange = { _, _ -> },
                    imageDependencies = ArticleImageDependencies(
                        articleImageLoader = mockk(relaxed = true),
                        coilImageLoader = ImageLoader.Builder(context).build(),
                        remoteResourcePolicy = mockk(relaxed = true)
                    ),
                )
            }
        }
    }
}

private class ArticleRowTestApplication : Application()
