package com.dugan.agent.ui.settings

import com.dugan.agent.domain.model.KeyTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyRowTest {

    @Test
    fun `an empty row is not dirty`() {
        assertFalse(KeyRow().isDirty)
    }

    @Test
    fun `typing a key marks the row dirty so Save enables`() {
        assertTrue(KeyRow(draft = "gsk_newkey123456").isDirty)
    }

    @Test
    fun `a blank draft is never dirty even with a stored key`() {
        assertFalse(KeyRow(draft = "", storedMask = "gsk_••••••••wxyz").isDirty)
    }

    @Test
    fun `whitespace only is not a change`() {
        assertFalse(KeyRow(draft = "   ").isDirty)
    }

    @Test
    fun `empty rows cover both providers`() {
        assertEquals(2, KeyRow.emptyRows().size)
        assertTrue(KeyRow.emptyRows().values.all { it.draft.isEmpty() && it.storedMask == null })
    }

    @Test
    fun `a fresh row reports untested`() {
        assertTrue(KeyRow().result is KeyTestResult.Untested)
    }
}
