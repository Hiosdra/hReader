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
import com.hiosdra.hreader.ui.article.FeedArticleListScreen
import com.hiosdra.hreader.ui.feeds.FeedDetailScreen
import com.hiosdra.hreader.ui.feeds.FeedsScreen
import com.hiosdra.hreader.ui.feeds.add.AddFeedScreen
import com.hiosdra.hreader.ui.main.MainScreen
import com.hiosdra.hreader.ui.settings.SettingsScreen

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(navController = navController)
        }
        composable("feeds") {
            FeedsScreen(navController = navController)
        }
        composable("add_feed") {
            AddFeedScreen(
                navController = navController,
                onFeedAdded = {
                    navController.popBackStack("feeds", inclusive = false)
                }
            )
        }
        composable(
            route = "article/{articleIds}/{initialIndex}",
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
            route = "feed/{feedId}",
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
        composable(
            route = "articles?feedId={feedId}",
            arguments = listOf(
                navArgument("feedId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val feedId = backStackEntry.arguments?.getLong("feedId")
            if (feedId != null) {
                FeedArticleListScreen(feedId = feedId, navController = navController)
            } else {
                Text(text = "Feed not found.")
            }
        }
        composable("settings") { backStackEntry ->
            val navController = navController
            SettingsScreen(navController)
        }
    }
}
