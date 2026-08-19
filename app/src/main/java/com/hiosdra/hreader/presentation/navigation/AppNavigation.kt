package com.hiosdra.hreader.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hiosdra.hreader.presentation.article.ArticleScreen
import com.hiosdra.hreader.presentation.feeds.FeedDetailScreen
import com.hiosdra.hreader.presentation.feeds.FeedsViewModel
import com.hiosdra.hreader.presentation.feeds.SubscriptionsDrawer
import com.hiosdra.hreader.presentation.feeds.rememberSubscriptionsDrawerState
import com.hiosdra.hreader.presentation.feeds.add.AddFeedScreen
import com.hiosdra.hreader.core.application.port.out.AppPreferences
import com.hiosdra.hreader.presentation.main.MainScreen
import com.hiosdra.hreader.presentation.onboarding.ServerSetupScreen
import com.hiosdra.hreader.presentation.settings.SettingsScreen
import com.hiosdra.hreader.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    entryPoint: EntryPoint = EntryPoint.ArticleList,
    preferencesManager: AppPreferences = koinInject()
) {
    var preferencesReady by remember(preferencesManager) { mutableStateOf(false) }
    LaunchedEffect(preferencesManager) {
        preferencesManager.awaitReady()
        preferencesReady = true
    }
    if (!preferencesReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val configured = remember { preferencesManager.hasBackendCredentials() }
    val startDestination = remember(entryPoint) {
        when {
            !configured -> Routes.SERVER_SETUP
            entryPoint is EntryPoint.AddFeed -> Routes.addFeed(entryPoint.url)
            else -> Routes.MAIN
        }
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left)
        },
        exitTransition = {
            fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left)
        },
        popEnterTransition = {
            fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right)
        },
        popExitTransition = {
            fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right)
        }
    ) {
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
                onNavigateBack = {
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
                Text(text = stringResource(R.string.feeds_not_found))
            }
        }
        composable(Routes.SETTINGS) { _ ->
            SettingsScreen(
                navController = navController,
                onSignedOut = {
                    navController.navigate(Routes.SERVER_SETUP) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                }
            )
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
    val feedsViewModel: FeedsViewModel = koinViewModel()
    var selectedFeedId by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(enabled = selectedFeedId != null) { selectedFeedId = null }

    val onFeedMarkedRead: (Long) -> Unit = { markedFeedId ->
        if (selectedFeedId == markedFeedId) {
            selectedFeedId = feedsViewModel.nextFeedId(markedFeedId)
        }
    }

    SubscriptionsDrawer(
        drawerState = drawerState,
        selectedFeedId = selectedFeedId,
        onSelectFeed = { selected -> selectedFeedId = selected },
        onFeedDetails = { navController.navigate(Routes.feed(it)) },
        onAddFeed = { navController.navigate(Routes.addFeed()) },
        viewModel = feedsViewModel,
        gesturesEnabled = true
    ) {
        MainScreen(
            navController = navController,
            onOpenSubscriptions = { scope.launch { drawerState.open() } },
            feedId = selectedFeedId,
            onLeaveFeed = { selectedFeedId = null },
            onFeedMarkedRead = onFeedMarkedRead
        )
    }
}
