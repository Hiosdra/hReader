package com.hiosdra.hreader.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
            MainWithSubscriptions(navController = navController)
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

/**
 * Which subscription the list is showing is state of this screen, not a destination of its own. As
 * a second route under the same `main` path it overlapped the one the app starts on, so opening a
 * feed left nothing distinct on the back stack and neither back gesture nor arrow could return to
 * all items. Leaving a feed is a state change instead, and both of them make it.
 */
@Composable
private fun MainWithSubscriptions(navController: NavHostController) {
    val drawerState = rememberSubscriptionsDrawerState()
    val scope = rememberCoroutineScope()
    var selectedFeedId by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(enabled = selectedFeedId != null) { selectedFeedId = null }

    SubscriptionsDrawer(
        drawerState = drawerState,
        selectedFeedId = selectedFeedId,
        onSelectFeed = { selected -> selectedFeedId = selected },
        onFeedDetails = { navController.navigate(Routes.feed(it)) },
        onAddFeed = { navController.navigate(Routes.addFeed()) },
        gesturesEnabled = selectedFeedId == null
    ) {
        MainScreen(
            navController = navController,
            onOpenSubscriptions = { scope.launch { drawerState.open() } },
            feedId = selectedFeedId,
            onLeaveFeed = { selectedFeedId = null }
        )
    }
}
