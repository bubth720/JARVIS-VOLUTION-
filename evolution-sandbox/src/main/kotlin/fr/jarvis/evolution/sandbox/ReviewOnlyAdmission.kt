package fr.jarvis.evolution.sandbox

import fr.jarvis.evolution.review.ReviewEntry
import fr.jarvis.evolution.review.ReviewStatus

/** Explicitly separates a human LAB review from an OS sandbox authorization. */
data class ReviewOnlyAdmission(val status: String, val reason: String,
    val canExecute: Boolean = false, val canApply: Boolean = false)

object ReviewOnlyAdmissionGate {
    fun inspect(entry: ReviewEntry?, now: Long): ReviewOnlyAdmission {
        if (entry == null || now < 0) return ReviewOnlyAdmission("UNKNOWN", "REVIEW_MISSING")
        if (now >= entry.metadata.expiresAt) return ReviewOnlyAdmission("BLOCKED", "REVIEW_EXPIRED")
        return when (entry.status) {
            ReviewStatus.PENDING -> ReviewOnlyAdmission("BLOCKED", "HUMAN_REVIEW_REQUIRED")
            ReviewStatus.APPROVED_FOR_LAB -> ReviewOnlyAdmission("BLOCKED", "SANDBOX_NOT_QUALIFIED")
            ReviewStatus.REJECTED -> ReviewOnlyAdmission("BLOCKED", "REVIEW_REJECTED")
            ReviewStatus.EXPIRED -> ReviewOnlyAdmission("BLOCKED", "REVIEW_EXPIRED")
        }
    }
}
