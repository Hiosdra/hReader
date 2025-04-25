package com.hiosdra.hreader.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hiosdra.hreader.ui.article.ArticleScreen
import com.hiosdra.hreader.ui.feeds.FeedsScreen
import com.hiosdra.hreader.ui.main.MainScreen

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
    }
}
