package com.hiosdra.hreader.presentation.article

import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View

internal class ReadAloudActionModeCallback(
    private val delegate: ActionMode.Callback,
    private val label: String,
    private val onReadAloud: (ActionMode) -> Unit
) : ActionMode.Callback2() {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val created = delegate.onCreateActionMode(mode, menu)
        addReadAloudItem(menu)
        return created
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val prepared = delegate.onPrepareActionMode(mode, menu)
        val added = addReadAloudItem(menu)
        return prepared || added
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId != READ_ALOUD_ITEM_ID) return delegate.onActionItemClicked(mode, item)
        onReadAloud(mode)
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        delegate.onDestroyActionMode(mode)
    }

    override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
        if (delegate is ActionMode.Callback2) {
            delegate.onGetContentRect(mode, view, outRect)
        } else {
            super.onGetContentRect(mode, view, outRect)
        }
    }

    private fun addReadAloudItem(menu: Menu): Boolean {
        if (menu.findItem(READ_ALOUD_ITEM_ID) != null) return false
        menu.add(Menu.NONE, READ_ALOUD_ITEM_ID, READ_ALOUD_ITEM_ORDER, label)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    internal companion object {
        const val READ_ALOUD_ITEM_ID = 0x7EAD
        private const val READ_ALOUD_ITEM_ORDER = 1
    }
}
