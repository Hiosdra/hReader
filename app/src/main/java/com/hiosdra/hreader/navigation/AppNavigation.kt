package com.hiosdra.hreader.navigation

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
    entryPoint: EntryPoint = EntryPoint.ArticleList,
    preferencesManager: PreferencesManager = koinInject()
) {
    val configured = remember { preferencesManager.hasBackendCredentials() }
    val startDestination = remember(entryPoint) {
        when {
            !configured -> Routes.SERVER_SETUP
            entryPoint is EntryPoint.AddFeed -> Routes.addFeed(entryPoint.url)
            else -> Routes.MAIN
        }
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
        composable(
            route = Routes.ADD_FEED,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            AddFeedScreen(
                navController = navController,
                initialUrl = backStackEntry.arguments?.getString("url"),
                // Opened from a share there is nothing behind this screen to go back to, so the
                // app lands on the article list rather than on an empty back stack.
                onFeedAdded = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(
            route = Routes.ARTICLE,
            arguments = listOf(
                navArgument("feedId") { type = NavType.LongType; defaultValue = Routes.FEED_ID_NONE },
                navArgument("startId") { type = NavType.LongType },
                navArgument("starred") { type = NavType.BoolType; defaultValue = false },
                navArgument("includeRead") { type = NavType.BoolType; defaultValue = false },
                navArgument("session") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val arguments = backStackEntry.arguments
            val rawFeedId = arguments?.getLong("feedId") ?: Routes.FEED_ID_NONE
            ArticleScreen(
                navController = navController,
                feedId = rawFeedId.takeIf { it != Routes.FEED_ID_NONE },
                startArticleId = arguments?.getLong("startId") ?: 0L,
                starredOnly = arguments?.getBoolean("starred") ?: false,
                includeRead = arguments?.getBoolean("includeRead") ?: false,
                sessionStartMillis = arguments?.getLong("session") ?: 0L
            )
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
        onAddFeed = { navController.navigate(Routes.addFeed()) },
        gesturesEnabled = feedId == null
    ) {
        MainScreen(
            navController = navController,
            onOpenSubscriptions = { scope.launch { drawerState.open() } },
            feedId = feedId
        )
    }
}
