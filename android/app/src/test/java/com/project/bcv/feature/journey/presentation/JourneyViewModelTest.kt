package com.project.bcv.feature.journey.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import com.project.bcv.core.network.ApiFailure
import com.project.bcv.feature.discovery.domain.ExploreJourney
import com.project.bcv.feature.discovery.domain.ExploreRepository
import com.project.bcv.feature.discovery.domain.ExploreSnapshot
import com.project.bcv.feature.discovery.domain.StepSource
import com.project.bcv.feature.location.startingpoint.domain.Coordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JourneyViewModelTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<JourneyViewModel>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    private fun create(repository: Repository = Repository(), steps: Steps = Steps()) =
        JourneyViewModel(repository, steps, dispatcher).also { models += it }

    private fun loaded(model: JourneyViewModel) = model.state.value as JourneyUiState.Loaded

    private fun runModelTest(body: suspend TestScope.() -> Unit) = runTest {
        try { body() } finally {
            // ViewModel jobs are not children of runTest. Stop its polling loop before runTest drains the scheduler.
            models.forEach { it.viewModelScope.cancel() }
            runCurrent()
        }
    }

    @Test fun syncSendsRawCumulativeStepsWithoutRecountingItemBonuses() = runModelTest {
        val repository = Repository(snapshot(recorded = 70, synced = 50))
        val steps = Steps(total = 60)
        val model = create(repository, steps)
        runCurrent()
        model.syncSteps()
        assertTrue(loaded(model).syncing)
        runCurrent()
        assertEquals(60L, repository.lastSubmitted)
        assertEquals(80, loaded(model).snapshot.journey!!.recordedSteps)
        assertEquals(60, loaded(model).snapshot.journey!!.syncedSteps)
        assertFalse(loaded(model).syncing)
    }

    @Test fun repeatedTapsWhileSyncingSendOnlyOneRequest() = runModelTest {
        val repository = Repository()
        val model = create(repository)
        runCurrent()
        model.syncSteps()
        model.syncSteps()
        model.load()
        runCurrent()
        assertEquals(1, repository.syncCalls)
        assertEquals(18, loaded(model).snapshot.journey!!.recordedSteps)
    }

    @Test fun serverFailureKeepsConfirmedProgressAndAllowsRetry() = runModelTest {
        val repository = Repository()
        val model = create(repository)
        runCurrent()
        val original = loaded(model)
        repository.syncFailure = ApiFailure("SERVICE_UNAVAILABLE", "Máy chủ chưa sẵn sàng")
        model.syncSteps()
        runCurrent()
        assertEquals(original.snapshot, loaded(model).snapshot)
        assertEquals(original.syncedAtMillis, loaded(model).syncedAtMillis)
        assertEquals("Máy chủ chưa sẵn sàng", loaded(model).syncError)
        assertFalse(loaded(model).syncing)
        repository.syncFailure = null
        model.syncSteps()
        runCurrent()
        assertNull(loaded(model).syncError)
        assertEquals(18, loaded(model).snapshot.journey!!.recordedSteps)
    }

    @Test fun manualSyncWithoutPermissionKeepsProgressAndRequestsPermissionOnce() = runModelTest {
        val repository = Repository()
        val steps = Steps(granted = false)
        val model = create(repository, steps)
        runCurrent()
        model.syncSteps()
        runCurrent()
        assertTrue(loaded(model).permissionRequired)
        assertEquals(10, loaded(model).snapshot.journey!!.recordedSteps)
        assertEquals(0, repository.syncCalls)
        assertEquals(1, steps.stopCalls)
        assertNotNull(model.permissionRequest.value!!.take())
        assertNull(model.permissionRequest.value!!.take())
    }

    @Test fun automaticSyncRunsOnlyWhileJourneyIsVisible() = runModelTest {
        val repository = Repository()
        val steps = Steps()
        val model = create(repository, steps)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(0, repository.syncCalls)
        model.setForeground(true)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, repository.syncCalls)
        model.setForeground(false)
        steps.total = 30
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, repository.syncCalls)
    }

    @Test fun automaticSyncWithoutPermissionDoesNotOpenRepeatedDialogs() = runModelTest {
        val repository = Repository()
        val model = create(repository, Steps(granted = false))
        runCurrent()
        model.setForeground(true)
        advanceTimeBy(6_000)
        runCurrent()
        assertTrue(loaded(model).permissionRequired)
        assertNull(model.permissionRequest.value)
        assertEquals(0, repository.syncCalls)
    }

    @Test fun completedJourneyDoesNotSubmitMoreSteps() = runModelTest {
        val repository = Repository(snapshot(recorded = 100, synced = 100, status = "COMPLETED"))
        val model = create(repository, Steps(total = 120))
        runCurrent()
        model.syncSteps()
        runCurrent()
        assertEquals(0, repository.syncCalls)
        assertEquals("COMPLETED", loaded(model).snapshot.journey!!.status)
    }

    @Test fun syncCanCompleteTheJourneyAndUpdatesTheTrackedSnapshot() = runModelTest {
        val steps = Steps(total = 120)
        val model = create(steps = steps)
        runCurrent()
        model.syncSteps()
        runCurrent()
        assertEquals(100, loaded(model).snapshot.journey!!.recordedSteps)
        assertEquals("COMPLETED", loaded(model).snapshot.journey!!.status)
        assertEquals("COMPLETED", steps.tracked?.status)
    }

    @Test fun expiredSessionStopsStepTracking() = runModelTest {
        val repository = Repository()
        val steps = Steps()
        val model = create(repository, steps)
        runCurrent()
        repository.syncFailure = ApiFailure("UNAUTHORIZED", "Phiên đã hết hạn")
        model.syncSteps()
        runCurrent()
        assertEquals(JourneyUiState.SessionExpired, model.state.value)
        assertEquals(1, steps.stopCalls)
    }

    private class Steps(var total: Long = 18, var granted: Boolean = true) : StepSource {
        override val usesDeviceSensor = true
        var tracked: ExploreJourney? = null
        var stopCalls = 0
        override suspend fun hasPermission() = granted
        override suspend fun totalStepsSince(startedAtEpochSeconds: Long) = total
        override fun track(journey: ExploreJourney?) { tracked = journey }
        override fun stopTracking() { stopCalls++ }
    }

    private class Repository(var current: ExploreSnapshot = snapshot()) : ExploreRepository {
        var syncCalls = 0
        var lastSubmitted: Long? = null
        var syncFailure: ApiFailure? = null
        override fun load() = current
        override fun syncSteps(journeyId: Long, cumulativeSteps: Long): ExploreSnapshot {
            syncCalls++
            lastSubmitted = cumulativeSteps
            syncFailure?.let { throw it }
            val journey = current.journey!!
            val raw = maxOf(journey.syncedSteps, cumulativeSteps.toInt())
            val recorded = minOf(journey.requiredSteps, journey.recordedSteps + raw - journey.syncedSteps)
            current = current.copy(journey = journey.copy(recordedSteps = recorded, syncedSteps = raw,
                status = if (recorded >= journey.requiredSteps) "COMPLETED" else "IN_PROGRESS"))
            return current
        }
        override fun loadPlace(placeId: Long) = error("Not used")
        override fun loadRecommendations() = error("Not used")
        override fun createJourney(placeId: Long, replaceActive: Boolean) = error("Not used")
    }

    companion object {
        private fun snapshot(recorded: Int = 10, synced: Int = 10, status: String = "IN_PROGRESS") =
            ExploreSnapshot(null, ExploreJourney(1, "Điểm đến", Coordinates(21.0, 105.0), 100,
                recorded, status, 100, syncedSteps = synced), emptyList())
    }
}
