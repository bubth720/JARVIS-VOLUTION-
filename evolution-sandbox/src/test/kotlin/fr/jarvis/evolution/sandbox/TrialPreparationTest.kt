package fr.jarvis.evolution.sandbox

import fr.jarvis.evolution.review.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.UUID

class TrialPreparationTest {
    private fun proposal()=VerifiedProposal(UUID.randomUUID().toString(),"a".repeat(64),"b".repeat(64),"c".repeat(40),"d".repeat(40),10000,listOf("src/Example.kt"))
    private fun metadata(p: VerifiedProposal)=ProposalMetadata(p.id,p.diffSha256,p.originSha256,p.baseCommit,p.unifiedCommit,p.expiresAt)
    @Test fun approvedAndRevokedStillNeverExecute() {
        val queue=HumanReviewQueue(Files.createTempDirectory("trial").resolve("review.log"));val p=proposal()
        assertEquals("REVIEW_REQUIRED",TrialPreparation.inspect(p,queue,100).reviewState)
        queue.register(metadata(p),100); assertEquals("PENDING",TrialPreparation.inspect(p,queue,100).reviewState)
        queue.decide(p.id,p.diffSha256,true,200)
        val result=TrialPreparation.inspect(p,queue,300)
        assertEquals("APPROVED_FOR_LAB",result.reviewState);assertFalse(result.mayExecute);assertFalse(result.productionAuthorized)
        assertEquals("SANDBOX_NOT_QUALIFIED",result.laboratoryState)
        assertEquals("NOT_RUN",RestrictedTrialRunner().run(p).status)
        queue.decide(p.id,p.diffSha256,false,400)
        assertEquals("REJECTED",TrialPreparation.inspect(p,queue,500).reviewState)
        assertEquals("EXPIRED",TrialPreparation.inspect(p,queue,10000).reviewState)
    }
    @Test fun differentDigestCannotBorrowApproval() {
        val queue=HumanReviewQueue(Files.createTempDirectory("trial").resolve("review.log"));val p=proposal()
        queue.register(metadata(p).copy(contentSha256="e".repeat(64)),100)
        assertEquals("METADATA_MISMATCH",TrialPreparation.inspect(p,queue,200).reviewState)
    }
}
