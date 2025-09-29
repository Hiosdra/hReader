package com.hiosdra.hreader.navigation

import android.util.Log
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hiosdra.hreader.ui.article.ArticleScreen
import com.hiosdra.hreader.ui.feeds.FeedDetailScreen
import com.hiosdra.hreader.ui.feeds.FeedsScreen
import com.hiosdra.hreader.ui.feeds.add.AddFeedScreen
import com.hiosdra.hreader.ui.main.MainScreen
import com.hiosdra.hreader.ui.settings.SettingsScreen

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(navController = navController)
        }
        composable(
            route = Routes.MAIN_WITH_OPTIONAL_FEED,
            arguments = listOf(
                navArgument("feedId") { type = NavType.LongType; defaultValue = Routes.FEED_ID_NONE }
            )
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getLong("feedId") ?: Routes.FEED_ID_NONE
            val feedId = if (raw == Routes.FEED_ID_NONE) null else raw
            MainScreen(navController = navController, feedId = feedId)
        }
        composable(Routes.FEEDS) { FeedsScreen(navController = navController) }
        composable(Routes.ADD_FEED) {
            AddFeedScreen(
                navController = navController,
                onFeedAdded = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("feed_added", true)
                    navController.popBackStack(Routes.FEEDS, inclusive = false)
                }
            )
        }
        composable(
            route = Routes.ARTICLE,
            arguments = listOf(
                navArgument("articleIds") { type = NavType.StringType },
                navArgument("initialIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val articleIdsString = backStackEntry.arguments?.getString("articleIds")
                ?: throw IllegalArgumentException("Article IDs are required when navigating to article screen")
            val initialIndex = backStackEntry.arguments?.getInt("initialIndex") ?: 0
            val articleIds = articleIdsString.split(",").mapNotNull { it.toLongOrNull() }
            Log.i("AppNavigation", "Article IDs: $articleIds, initialIndex: $initialIndex")
            ArticleScreen(navController, articleIds, initialIndex)
        }
        composable(
            route = Routes.FEED,
            arguments = listOf(
                navArgument("feedId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val feedId = backStackEntry.arguments?.getLong("feedId")
            if (feedId != null) {
                FeedDetailScreen(feedId = feedId, navController = navController)
            } else {
                Text(text = "Feed not found.")
            }
        }
        composable(Routes.SETTINGS) { _ ->
            SettingsScreen(navController)
        }
    }
}
