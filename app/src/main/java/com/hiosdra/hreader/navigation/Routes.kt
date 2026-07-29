package com.hiosdra.hreader.navigation

object Routes {
    const val FEED_ID_NONE: Long = -1L

    const val SERVER_SETUP = "server_setup"
    const val MAIN = "main"
    const val MAIN_WITH_OPTIONAL_FEED = "main?feedId={feedId}"
    const val FEED = "feed/{feedId}"
    const val ADD_FEED = "add_feed"
    const val ARTICLE = "article/{articleIds}/{initialIndex}"
    const val SETTINGS = "settings"

    fun main(feedId: Long?): String = if (feedId == null) MAIN else "$MAIN?feedId=$feedId"
    fun article(articleIds: List<Long>, initialIndex: Int): String =
        "article/${articleIds.joinToString(",")}/$initialIndex"
    fun feed(feedId: Long): String = "feed/$feedId"
}
