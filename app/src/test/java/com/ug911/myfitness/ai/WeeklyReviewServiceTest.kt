package com.ug911.myfitness.ai

import com.ug911.myfitness.TestData
import com.ug911.myfitness.data.local.dao.AiAnalysisDao
import com.ug911.myfitness.data.local.entity.AiAnalysisEntity
import com.ug911.myfitness.data.model.JournalEntry
import com.ug911.myfitness.data.model.Plan
import com.ug911.myfitness.data.model.PlanStatus
import com.ug911.myfitness.data.model.PlanTarget
import com.ug911.myfitness.data.repository.AnalysisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyReviewServiceTest {

    private val strength = TestData.habit(1, "Strength training")
    private val energy = TestData.rating(2, "Energy")

    private val reply = """
        {
          "review": "Three sessions, energy steady.",
          "insights": ["Early nights tracked with better energy."],
          "plan": {
            "rationale": "One more session.",
            "targets": [
              {"tracker": "Strength training", "target_frequency": 4},
              {"tracker": "Ice bath", "target_frequency": 2}
            ]
          }
        }
    """.trimIndent()

    private class FakeClient(private val reply: String, val fail: Boolean = false) : AiClient {
        override val providerId = "fake"
        override val model = "fake-model"
        var lastUserMessage: String? = null
        override suspend fun complete(system: String, user: String): String {
            lastUserMessage = user
            if (fail) throw AiRequestException("boom")
            return reply
        }
    }

    private class RecordingHistory(private val data: (LocalDate, LocalDate) -> PeriodData) : HistorySource {
        val requested = mutableListOf<Pair<LocalDate, LocalDate>>()
        override suspend fun load(start: LocalDate, end: LocalDate): PeriodData {
            requested += start to end
            return data(start, end)
        }
    }

    private class RecordingProposals : PlanProposalSink {
        val proposed = mutableListOf<Pair<Plan, List<PlanTarget>>>()
        override suspend fun propose(plan: Plan, targets: List<PlanTarget>): Long {
            proposed += plan to targets
            return 7L
        }
    }

    private class FakeAnalysisDao : AiAnalysisDao {
        val saved = mutableListOf<AiAnalysisEntity>()
        override fun observeAll(): Flow<List<AiAnalysisEntity>> = flowOf(saved)
        override fun observeLatest(): Flow<AiAnalysisEntity?> = flowOf(saved.lastOrNull())
        override suspend fun insert(analysis: AiAnalysisEntity): Long {
            saved += analysis
            return saved.size.toLong()
        }
    }

    private fun populatedWeek(start: LocalDate, end: LocalDate) = PeriodData(
        start = start,
        end = end,
        trackers = listOf(strength, energy),
        entries = listOf(
            TestData.flag(strength, start),
            TestData.flag(strength, start.plusDays(2)),
            TestData.flag(strength, start.plusDays(4)),
            TestData.score(energy, start, 4),
        ),
        journal = listOf(JournalEntry(start, "Felt strong")),
    )

    @Test
    fun `a successful review stores the analysis and proposes an unapproved plan`() = runTest {
        val history = RecordingHistory(::populatedWeek)
        val proposals = RecordingProposals()
        val dao = FakeAnalysisDao()
        val client = FakeClient(reply)
        val service = WeeklyReviewService(history, proposals, AnalysisRepository(dao))
        val period = ReviewPeriod(TestData.monday, TestData.sunday)

        val outcome = service.review(period, client)

        assertTrue(outcome is ReviewOutcome.Success)
        val success = outcome as ReviewOutcome.Success
        assertEquals(7L, success.proposedPlanId)
        // The unknown tracker is reported rather than silently accepted.
        assertEquals(listOf("Ice bath"), success.unmatchedTargets)

        val (plan, targets) = proposals.proposed.single()
        assertEquals(PlanStatus.PROPOSED, plan.status)
        assertEquals(TestData.sunday.plusDays(1), plan.startDate)
        assertEquals(1, targets.size)
        assertEquals(strength.id, targets.single().trackerId)

        val stored = dao.saved.single()
        assertEquals("fake", stored.provider)
        assertEquals("Three sessions, energy steady.", stored.review)
        // The exact snapshot that was sent is kept, so a review can be re-read later.
        assertTrue(stored.inputSnapshot.contains("strength_training"))
        assertTrue(client.lastUserMessage!!.contains("strength_training"))
    }

    @Test
    fun `the review compares against the previous week`() = runTest {
        val history = RecordingHistory(::populatedWeek)
        val service = WeeklyReviewService(history, RecordingProposals(), AnalysisRepository(FakeAnalysisDao()))

        service.review(ReviewPeriod(TestData.monday, TestData.sunday), FakeClient(reply))

        assertEquals(
            listOf(
                TestData.monday to TestData.sunday,
                TestData.monday.minusDays(7) to TestData.monday.minusDays(1),
            ),
            history.requested,
        )
    }

    @Test
    fun `a failed model call keeps the snapshot and writes nothing`() = runTest {
        val dao = FakeAnalysisDao()
        val proposals = RecordingProposals()
        val service = WeeklyReviewService(RecordingHistory(::populatedWeek), proposals, AnalysisRepository(dao))

        val outcome = service.review(ReviewPeriod(TestData.monday, TestData.sunday), FakeClient("", fail = true))

        assertTrue(outcome is ReviewOutcome.Failed)
        assertTrue((outcome as ReviewOutcome.Failed).snapshot.contains("period"))
        assertTrue(dao.saved.isEmpty())
        assertTrue(proposals.proposed.isEmpty())
    }

    @Test
    fun `an unreadable reply is a failure, not a half-saved review`() = runTest {
        val dao = FakeAnalysisDao()
        val proposals = RecordingProposals()
        val service = WeeklyReviewService(RecordingHistory(::populatedWeek), proposals, AnalysisRepository(dao))

        val outcome = service.review(ReviewPeriod(TestData.monday, TestData.sunday), FakeClient("no json here"))

        assertTrue(outcome is ReviewOutcome.Failed)
        assertTrue(dao.saved.isEmpty())
        assertTrue(proposals.proposed.isEmpty())
    }

    @Test
    fun `an empty period is refused before any model call`() = runTest {
        val empty = RecordingHistory { start, end ->
            PeriodData(start, end, listOf(strength), emptyList(), emptyList())
        }
        val client = FakeClient(reply)
        val service = WeeklyReviewService(empty, RecordingProposals(), AnalysisRepository(FakeAnalysisDao()))

        val outcome = service.review(ReviewPeriod(TestData.monday, TestData.sunday), client)

        assertEquals(ReviewOutcome.NothingLogged, outcome)
        assertNull(client.lastUserMessage)
    }

    @Test
    fun `snapshot preview does not call the model`() = runTest {
        val service = WeeklyReviewService(
            RecordingHistory(::populatedWeek),
            RecordingProposals(),
            AnalysisRepository(FakeAnalysisDao()),
        )

        val snapshot = service.buildSnapshot(ReviewPeriod(TestData.monday, TestData.sunday))

        assertNotNull(snapshot)
        assertTrue(snapshot.contains("\"period\""))
    }

    @Test
    fun `review periods run monday to sunday`() {
        val wednesday = LocalDate.of(2026, 9, 16)

        val thisWeek = ReviewPeriod.weekContaining(wednesday)
        assertEquals(LocalDate.of(2026, 9, 14), thisWeek.start)
        assertEquals(LocalDate.of(2026, 9, 20), thisWeek.end)

        val lastWeek = ReviewPeriod.lastCompleteWeek(wednesday)
        assertEquals(LocalDate.of(2026, 9, 7), lastWeek.start)
        assertEquals(LocalDate.of(2026, 9, 13), lastWeek.end)
    }
}
