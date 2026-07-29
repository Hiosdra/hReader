package com.hiosdra.hreader.navigation

import android.util.Log
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hiosdra.hreader.ui.article.ArticleScreen
import com.hiosdra.hreader.ui.feeds.FeedDetailScreen
import com.hiosdra.hreader.ui.feeds.SubscriptionsDrawer
import com.hiosdra.hreader.ui.feeds.rememberSubscriptionsDrawerState
import com.hiosdra.hreader.ui.feeds.add.AddFeedScreen
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.ui.main.MainScreen
import com.hiosdra.hreader.ui.onboarding.ServerSetupScreen
import com.hiosdra.hreader.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    preferencesManager: PreferencesManager = koinInject()
) {
    val startDestination = remember {
        if (preferencesManager.hasBackendCredentials()) Routes.MAIN else Routes.SERVER_SETUP
    }
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SERVER_SETUP) {
            ServerSetupScreen(
                onSetupFinished = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SERVER_SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.MAIN) {
            MainWithSubscriptions(navController = navController, feedId = null)
        }
        composable(
            route = Routes.MAIN_WITH_OPTIONAL_FEED,
            arguments = listOf(
                navArgument("feedId") { type = NavType.LongType; defaultValue = Routes.FEED_ID_NONE }
            )
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getLong("feedId") ?: Routes.FEED_ID_NONE
            val feedId = if (raw == Routes.FEED_ID_NONE) null else raw
            MainWithSubscriptions(navController = navController, feedId = feedId)
        }
        composable(Routes.ADD_FEED) {
            AddFeedScreen(
                navController = navController,
                onFeedAdded = { navController.popBackStack() }
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

@Composable
private fun MainWithSubscriptions(navController: NavHostController, feedId: Long?) {
    val drawerState = rememberSubscriptionsDrawerState()
    val scope = rememberCoroutineScope()

    SubscriptionsDrawer(
        drawerState = drawerState,
        selectedFeedId = feedId,
        onSelectFeed = { selected ->
            if (selected != feedId) {
                navController.navigate(Routes.main(selected)) {
                    popUpTo(Routes.MAIN) { inclusive = selected == null }
                    launchSingleTop = true
                }
            }
        },
        onFeedDetails = { navController.navigate(Routes.feed(it)) },
        onAddFeed = { navController.navigate(Routes.ADD_FEED) },
        gesturesEnabled = feedId == null
    ) {
        MainScreen(
            navController = navController,
            onOpenSubscriptions = { scope.launch { drawerState.open() } },
            feedId = feedId
        )
    }
}
