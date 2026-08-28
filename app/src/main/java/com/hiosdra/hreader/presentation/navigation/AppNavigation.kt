package com.hiosdra.hreader.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import coil3.ImageLoader as CoilImageLoader
import com.hiosdra.hreader.presentation.article.ArticleImageDependencies
import com.hiosdra.hreader.presentation.article.ArticleScreen
import com.hiosdra.hreader.core.application.port.out.ArticleImageDownloader
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.ArticleImageSharer
import com.hiosdra.hreader.core.application.port.out.ArticleTtsPlayer
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.port.out.BackendPreferences
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.GemmaModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.GemmaModelGateway
import com.hiosdra.hreader.core.application.port.out.GemmaModelLifecycle
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.application.port.out.PaywallBypass
import com.hiosdra.hreader.core.application.port.out.PerformancePreferences
import com.hiosdra.hreader.core.application.port.out.ReaderPreferences
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import com.hiosdra.hreader.core.application.port.out.TtsPreferences
import com.hiosdra.hreader.core.application.port.out.TtsModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.presentation.feeds.FeedDetailScreen
import com.hiosdra.hreader.presentation.feeds.FeedsViewModel
import com.hiosdra.hreader.presentation.feeds.SubscriptionsDrawer
import com.hiosdra.hreader.presentation.feeds.rememberSubscriptionsDrawerState
import com.hiosdra.hreader.presentation.feeds.add.AddFeedScreen
import com.hiosdra.hreader.presentation.main.MainScreen
import com.hiosdra.hreader.presentation.main.MainViewModel
import com.hiosdra.hreader.presentation.onboarding.ServerSetupScreen
import com.hiosdra.hreader.presentation.settings.SettingsScreen
import com.hiosdra.hreader.presentation.settings.TtsSettingsScreen
import com.hiosdra.hreader.presentation.theme.MotionDuration
import com.hiosdra.hreader.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    entryPoint: EntryPoint = EntryPoint.ArticleList,
    backendPreferences: BackendPreferences = koinInject(),
    readerPreferences: ReaderPreferences = koinInject(),
    ttsPreferences: TtsPreferences = koinInject(),
    aiPreferences: AiPreferences = koinInject(),
    performancePreferences: PerformancePreferences = koinInject(),
    errorReporter: ErrorReporter = koinInject(),
    ttsModelManager: TtsModelGateway = koinInject(),
    ttsModelDownloadScheduler: TtsModelDownloadRequester = koinInject(),
    gemmaModelManager: GemmaModelGateway = koinInject(),
    gemmaModelDownloadScheduler: GemmaModelDownloadRequester = koinInject(),
    gemmaModelLifecycle: GemmaModelLifecycle = koinInject(),
    paywallBypass: PaywallBypass = koinInject(),
    articleTtsPlayer: ArticleTtsPlayer = koinInject(),
    articleImageLoader: ArticleImageLoader = koinInject(),
    coilImageLoader: CoilImageLoader = koinInject(),
    remoteResourcePolicy: RemoteResourcePolicy = koinInject(),
    articleImageSharer: ArticleImageSharer = koinInject(),
    articleImageDownloader: ArticleImageDownloader = koinInject(),
    networkStatus: NetworkStatus = koinInject()
) {
    val configured = remember { backendPreferences.hasBackendCredentials() }
    val startDestination = remember(entryPoint) {
        when {
            !configured -> Routes.SERVER_SETUP
            entryPoint is EntryPoint.AddFeed -> Routes.addFeed(entryPoint.url)
            else -> Routes.MAIN
        }
    }
    val articleImageDependencies = remember(
        articleImageLoader,
        coilImageLoader,
        remoteResourcePolicy
    ) {
        ArticleImageDependencies(
            articleImageLoader = articleImageLoader,
            coilImageLoader = coilImageLoader,
            remoteResourcePolicy = remoteResourcePolicy
        )
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))
                )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.STANDARD))
                )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(MotionDuration.scaled(MotionDuration.EXIT))
                )
        }
    ) {
        composable(Routes.SERVER_SETUP) {
            ServerSetupScreen(
                settingsViewModel = koinViewModel(),
                errorReportingManager = errorReporter,
                onSetupFinished = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SERVER_SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.MAIN) {
            MainWithSubscriptions(
                navController = navController,
                mainViewModel = koinViewModel(),
                feedsViewModel = koinViewModel(),
                networkStatus = networkStatus,
                imageDependencies = articleImageDependencies
            )
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
                addFeedViewModel = koinViewModel(),
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
                includeRead = arguments?.getBoolean("includeRead") ?: false,
                sessionStartMillis = arguments?.getLong("session") ?: 0L,
                readerPreferences = readerPreferences,
                ttsPreferences = ttsPreferences,
                paywallBypassService = paywallBypass,
                ttsModelManager = ttsModelManager,
                ttsController = articleTtsPlayer,
                articleImageLoader = articleImageLoader,
                coilImageLoader = coilImageLoader,
                remoteResourcePolicy = remoteResourcePolicy,
                articleImageSharer = articleImageSharer,
                articleImageDownloader = articleImageDownloader,
                viewModel = koinViewModel()
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
                FeedDetailScreen(
                    feedId = feedId,
                    navController = navController,
                    viewModel = koinViewModel()
                )
            } else {
                Text(text = stringResource(R.string.feeds_not_found))
            }
        }
        composable(Routes.SETTINGS) { _ ->
            SettingsScreen(
                navController = navController,
                readerPreferences = readerPreferences,
                ttsPreferences = ttsPreferences,
                aiPreferences = aiPreferences,
                performancePreferences = performancePreferences,
                errorReportingManager = errorReporter,
                gemmaModelManager = gemmaModelManager,
                gemmaModelDownloadScheduler = gemmaModelDownloadScheduler,
                gemmaModelLifecycle = gemmaModelLifecycle,
                settingsViewModel = koinViewModel(),
                onSignedOut = {
                    navController.navigate(Routes.SERVER_SETUP) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.TTS_SETTINGS) {
            TtsSettingsScreen(
                navController = navController,
                ttsPreferences = ttsPreferences,
                ttsModelManager = ttsModelManager,
                ttsModelDownloadScheduler = ttsModelDownloadScheduler
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
private fun MainWithSubscriptions(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    feedsViewModel: FeedsViewModel,
    networkStatus: NetworkStatus,
    imageDependencies: ArticleImageDependencies
) {
    val drawerState = rememberSubscriptionsDrawerState()
    val scope = rememberCoroutineScope()
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
        networkStatus = networkStatus,
        gesturesEnabled = true
    ) {
        MainScreen(
            navController = navController,
            onOpenSubscriptions = { scope.launch { drawerState.open() } },
            feedId = selectedFeedId,
            viewModel = mainViewModel,
            imageDependencies = imageDependencies,
            onLeaveFeed = { selectedFeedId = null },
            onFeedMarkedRead = onFeedMarkedRead
        )
    }
}
