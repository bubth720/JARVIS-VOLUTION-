package fr.jarvis.evolution.sandbox

import fr.jarvis.evolution.review.*

data class TrialPreparationResult(val reviewState: String,val laboratoryState: String="SANDBOX_NOT_QUALIFIED",
    val mayExecute: Boolean=false,val productionAuthorized: Boolean=false)

/** Prepares evidence for a trusted host's human review. No runner, shell or unlock mechanism.
 * Re-read the durable queue so revocation/expiry is not replaced by a cached approval. */
object TrialPreparation {
    fun inspect(proposal: VerifiedProposal,queue: HumanReviewQueue,now: Long): TrialPreparationResult {
        require(now>=0)
        if(now>=proposal.expiresAt) return TrialPreparationResult("EXPIRED")
        val entry=queue.list(now).find { it.metadata.id==proposal.id } ?: return TrialPreparationResult("REVIEW_REQUIRED")
        val m=entry.metadata
        if(m.contentSha256!=proposal.diffSha256 || m.originSha256!=proposal.originSha256 ||
            m.baseCommit!=proposal.baseCommit || m.unifiedCommit!=proposal.unifiedCommit || m.expiresAt!=proposal.expiresAt)
            return TrialPreparationResult("METADATA_MISMATCH")
        return TrialPreparationResult(entry.status.name)
    }
}
