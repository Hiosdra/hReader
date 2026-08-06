package com.hiosdra.hreader.ui.feeds

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val SheetShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)

@Composable
fun rememberSubscriptionsDrawerState(): DrawerState =
    rememberDrawerState(initialValue = DrawerValue.Closed)

@Composable
fun SubscriptionsDrawer(
    drawerState: DrawerState,
    selectedFeedId: Long?,
    onSelectFeed: (Long?) -> Unit,
    onFeedDetails: (Long) -> Unit,
    onAddFeed: () -> Unit,
    viewModel: FeedsViewModel,
    gesturesEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val closeThen: (() -> Unit) -> Unit = { action ->
        scope.launch {
            drawerState.close()
            action()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled || drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerState = drawerState, drawerShape = SheetShape) {
                SubscriptionsPanel(
                    selectedFeedId = selectedFeedId,
                    visible = drawerState.isOpen,
                    onSelectFeed = { feedId -> closeThen { onSelectFeed(feedId) } },
                    onFeedDetails = { feedId -> closeThen { onFeedDetails(feedId) } },
                    onAddFeed = { closeThen(onAddFeed) },
                    viewModel = viewModel
                )
            }
        },
        content = content
    )
}
