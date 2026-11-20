package com.hiosdra.hreader.presentation.article

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudActionModeCallbackTest {
    private val delegate = mockk<ActionMode.Callback>(relaxed = true)
    private val mode = mockk<ActionMode>(relaxed = true)
    private val addedItem = mockk<MenuItem>(relaxed = true)
    private val menu = mockk<Menu>(relaxed = true) {
        every { findItem(ReadAloudActionModeCallback.READ_ALOUD_ITEM_ID) } returns null
        every { add(any(), any(), any(), any<CharSequence>()) } returns addedItem
    }

    @Test
    fun `adds the read aloud action to the selection menu`() {
        every { delegate.onCreateActionMode(mode, menu) } returns true
        val callback = ReadAloudActionModeCallback(delegate, "Read aloud from here") {}

        assertTrue(callback.onCreateActionMode(mode, menu))

        verify { delegate.onCreateActionMode(mode, menu) }
        verify {
            menu.add(Menu.NONE, ReadAloudActionModeCallback.READ_ALOUD_ITEM_ID, any(), "Read aloud from here")
        }
    }

    @Test
    fun `handles its own action without delegating`() {
        val readAloudModes = mutableListOf<ActionMode>()
        val callback = ReadAloudActionModeCallback(delegate, "Read") { readAloudModes += it }
        val item = mockk<MenuItem> { every { itemId } returns ReadAloudActionModeCallback.READ_ALOUD_ITEM_ID }

        assertTrue(callback.onActionItemClicked(mode, item))

        assertEquals(listOf(mode), readAloudModes)
        verify(exactly = 0) { delegate.onActionItemClicked(any(), any()) }
    }

    @Test
    fun `delegates other selection actions`() {
        val callback = ReadAloudActionModeCallback(delegate, "Read") { error("unexpected") }
        val copy = mockk<MenuItem> { every { itemId } returns android.R.id.copy }
        every { delegate.onActionItemClicked(mode, copy) } returns true

        assertTrue(callback.onActionItemClicked(mode, copy))

        verify { delegate.onActionItemClicked(mode, copy) }
    }
}
