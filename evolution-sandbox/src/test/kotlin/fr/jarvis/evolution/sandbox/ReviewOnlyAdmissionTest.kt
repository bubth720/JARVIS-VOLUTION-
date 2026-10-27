package fr.jarvis.evolution.sandbox

import fr.jarvis.evolution.review.ProposalMetadata
import fr.jarvis.evolution.review.ReviewEntry
import fr.jarvis.evolution.review.ReviewStatus
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ReviewOnlyAdmissionTest {
    private val metadata = ProposalMetadata(UUID.randomUUID().toString(),
        "a".repeat(64), "b".repeat(64), "c".repeat(40), "d".repeat(40), 2000)
    private fun entry(status: ReviewStatus) = ReviewEntry(metadata, status)

    @Test fun approvedLabReviewNeverBecomesExecutionPermission() {
        val view = ReviewOnlyAdmissionGate.inspect(entry(ReviewStatus.APPROVED_FOR_LAB), 1000)
        assertEquals("BLOCKED", view.status)
        assertEquals("SANDBOX_NOT_QUALIFIED", view.reason)
        assertFalse(view.canExecute)
        assertFalse(view.canApply)
    }

    @Test fun expiredRejectedAndPendingAlwaysRefuse() {
        for (state in ReviewStatus.entries) {
            val view = ReviewOnlyAdmissionGate.inspect(entry(state), 1000)
            assertFalse(view.canExecute)
            assertFalse(view.canApply)
        }
        assertEquals("REVIEW_EXPIRED",
            ReviewOnlyAdmissionGate.inspect(entry(ReviewStatus.APPROVED_FOR_LAB), 2000).reason)
    }

    @Test fun absentReviewAndInvalidClockNeverPermitEffects() {
        assertEquals("UNKNOWN", ReviewOnlyAdmissionGate.inspect(null, 1000).status)
        assertFalse(ReviewOnlyAdmissionGate.inspect(null, 1000).canExecute)
        assertFalse(ReviewOnlyAdmissionGate.inspect(entry(ReviewStatus.APPROVED_FOR_LAB), -1).canApply)
    }
}
