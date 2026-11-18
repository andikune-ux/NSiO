package com.example.embedded

import com.example.embedded.engine.EmbeddedModuleManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EmbeddedModuleManagerTest {
    private val moduleId = "dev.andikune.nsio.ddtv"
    private lateinit var manager: EmbeddedModuleManager

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("embedded_modules", 0).edit().clear().commit()
        manager = EmbeddedModuleManager(context)
    }

    @Test
    fun builtInModuleIsEnabledAndCanStartAndStop() {
        assertTrue(manager.states.value.single { it.metadata.id == moduleId }.enabled)

        manager.start(moduleId)
        assertTrue(manager.states.value.single { it.metadata.id == moduleId }.running)

        // Repeated start is idempotent and must not duplicate lifecycle callbacks.
        manager.start(moduleId)
        assertTrue(manager.states.value.single { it.metadata.id == moduleId }.running)

        manager.stop(moduleId)
        assertFalse(manager.states.value.single { it.metadata.id == moduleId }.running)
    }

    @Test
    fun disablingModuleStopsItAndPreventsRestart() {
        manager.start(moduleId)
        manager.setEnabled(moduleId, false)

        val state = manager.states.value.single { it.metadata.id == moduleId }
        assertFalse(state.enabled)
        assertFalse(state.running)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            manager.start(moduleId)
        }
    }

    @Test
    fun destroyAllowsCleanStartAgain() {
        manager.start(moduleId)
        manager.destroy(moduleId)
        assertFalse(manager.states.value.single { it.metadata.id == moduleId }.running)

        manager.start(moduleId)
        assertTrue(manager.states.value.single { it.metadata.id == moduleId }.running)
    }
}
