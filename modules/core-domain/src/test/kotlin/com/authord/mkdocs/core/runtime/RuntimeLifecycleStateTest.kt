package com.authord.mkdocs.core.runtime

import kotlin.test.Test
import kotlin.test.assertTrue

class RuntimeLifecycleStateTest {
    @Test
    fun `runtime lifecycle enum exposes all expected states`() {
        val names = RuntimeLifecycleState.entries.map { it.name }.toSet()

        assertTrue("UNINITIALIZED" in names)
        assertTrue("BOOTSTRAPPING" in names)
        assertTrue("READY" in names)
        assertTrue("SERVING" in names)
        assertTrue("RESTARTING" in names)
        assertTrue("STOPPING" in names)
        assertTrue("STOPPED" in names)
        assertTrue("FAILED" in names)
        assertTrue("DISPOSED" in names)
    }
}
