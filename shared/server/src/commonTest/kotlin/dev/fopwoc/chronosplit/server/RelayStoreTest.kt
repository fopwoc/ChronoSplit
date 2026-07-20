package dev.fopwoc.chronosplit.server

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayStoreTest {
    @Test
    fun onlyOneMobileSessionOwnsTheRelay() = runTest {
        val store = RelayStore()

        assertTrue(store.tryClaimMobileSession("first"))
        assertFalse(store.tryClaimMobileSession("second"))

        store.releaseMobileSession("second")
        assertFalse(store.tryClaimMobileSession("second"))

        store.releaseMobileSession("first")
        assertTrue(store.tryClaimMobileSession("second"))
    }
}
