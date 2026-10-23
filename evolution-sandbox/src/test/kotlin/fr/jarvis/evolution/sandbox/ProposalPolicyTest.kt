package fr.jarvis.evolution.sandbox

import fr.jarvis.evolution.review.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature

class ProposalPolicyTest {
    private val base="a".repeat(40)
    private val unified="bd283dad3a57effa276f7f4441901839d2834d5c"
    private val patch="diff --git a/src/Synthetic.kt b/src/Synthetic.kt\nindex 1111111..2222222 100644\n--- a/src/Synthetic.kt\n+++ b/src/Synthetic.kt\n@@ -1 +1 @@\n-fun answer() = 1\n+fun answer() = 2\n"
    private fun policy()=DiffPolicyValidator(setOf("src/Synthetic.kt"))
    private fun signed():Pair<ProposalVerifier,PatchProposal> {
        val pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val origin=DiffPolicyValidator.sha256(pair.public.encoded)
        val draft=PatchProposal(1,"EVOL-03",base,unified,patch,policy().validate(patch).sha256,origin,2000,byteArrayOf())
        val sig=Signature.getInstance("Ed25519").run{initSign(pair.private);update(draft.signingBytes());sign()}
        return ProposalVerifier(policy(),mapOf(origin to pair.public),unified,base) to draft.copy(signature=sig)
    }
    private fun metadata():ProposalMetadata {
        val (verifier,proposal)=signed();val p=verifier.verify(proposal,1000)
        return ProposalMetadata(p.id,p.diffSha256,p.originSha256,p.baseCommit,p.unifiedCommit,p.expiresAt)
    }
    @Test fun realTextDiffIsParsedWithoutApplyingIt() {
        val summary=policy().validate(patch)
        assertEquals(listOf("src/Synthetic.kt"),summary.files);assertEquals(1,summary.added);assertEquals(1,summary.removed)
        assertEquals(DiffPolicyValidator.sha256(patch.toByteArray()),summary.sha256)
    }
    @Test fun unsafeNamesBinaryRenamesModesAndExecutablePathsAreRefused() {
        for(diff in listOf(patch.replace("src/Synthetic.kt","../secret.kt"),patch.replace("src/Synthetic.kt","/tmp/secret.kt"),
            patch.replace("src/Synthetic.kt","src/.git/config.kt"),patch.replace("100644","120000"),patch.replace("100644","100755"),
            patch.replace("--- a/src/Synthetic.kt","--- /dev/null"),patch.replace("+++ b/src/Synthetic.kt","+++ b/src/Other.kt"),
            "GIT binary patch\nliteral 4\n",patch.replace("index 1111111..2222222 100644","rename from src/Synthetic.kt")))
            assertThrows(IllegalArgumentException::class.java){policy().validate(diff)}
    }
    @Test fun truncatedAndMismatchedHunksCannotBeAccepted() {
        for(diff in listOf(patch.substringBefore("+fun")+"\n",patch.replace("@@ -1 +1 @@","@@ -1,2 +1 @@"),
            patch.replace("@@ -1 +1 @@","@@ -999999999999999 +1 @@"),patch+patch,patch.replace("-fun answer() = 1","not-a-hunk")))
            assertThrows(IllegalArgumentException::class.java){policy().validate(diff)}
    }
    @Test fun invalidUtf8ControlCharactersAndOversizedDiffsAreRefused() {
        for(diff in listOf(patch+"\u0000",patch.replace("answer","\uD800"),patch.replace("\n","\r\n"),patch+"x".repeat(65536)))
            assertThrows(IllegalArgumentException::class.java){policy().validate(diff)}
    }
    @Test fun declaredFilesAreReadOnlyAndOnlySyntheticRootsAreEligible() {
        val dir=Files.createTempDirectory("evol-policy-synthetic-");Files.createDirectory(dir.resolve("src"))
        val file=dir.resolve("src/Synthetic.kt");Files.writeString(file,"fun answer() = 1\n")
        val before=Files.readAllBytes(file);val summary=policy().validate(patch)
        assertThrows(IllegalArgumentException::class.java){policy().validateSyntheticFiles(dir,summary)}
        Files.writeString(dir.resolve(".jarvis-synthetic-workspace"),"EVOL-03-SYNTHETIC\n")
        policy().validateSyntheticFiles(dir,summary);assertArrayEquals(before,Files.readAllBytes(file))
    }
    @Test fun symlinkToEvenAnotherSyntheticFileIsRefused() {
        val dir=Files.createTempDirectory("evol-policy-synthetic-");Files.createDirectory(dir.resolve("src"))
        Files.writeString(dir.resolve(".jarvis-synthetic-workspace"),"EVOL-03-SYNTHETIC\n")
        val outside=Files.createTempFile("evol-outside-synthetic-",".kt");Files.writeString(outside,"fixture")
        Files.createSymbolicLink(dir.resolve("src/Synthetic.kt"),outside)
        assertThrows(IllegalArgumentException::class.java){policy().validateSyntheticFiles(dir,policy().validate(patch))}
        assertEquals("fixture",Files.readString(outside))
    }
    @Test fun actualEd25519OriginSignatureIsVerifiedAgainstPinnedKey() {
        val (verifier,proposal)=signed();val verified=verifier.verify(proposal,1000)
        assertEquals(proposal.diffSha256,verified.diffSha256);assertEquals(base,verified.baseCommit)
    }
    @Test fun forgedOriginSignatureAndDigestAreRefused() {
        val (verifier,p)=signed()
        for(proposal in listOf(p.copy(signature=ByteArray(64)),p.copy(originSha256="0".repeat(64)),p.copy(diffSha256="0".repeat(64))))
            assertThrows(Exception::class.java){verifier.verify(proposal,1000)}
    }
    @Test fun expiredWrongBaseVersionAndUnifiedReferenceAreRefused() {
        val (verifier,p)=signed()
        for(proposal in listOf(p.copy(expiresAt=1000),p.copy(baseCommit="b".repeat(40)),p.copy(unifiedCommit="c".repeat(40)),p.copy(version=2),p.copy(mission="EVOL-04")))
            assertThrows(IllegalArgumentException::class.java){verifier.verify(proposal,1000)}
    }
    @Test fun evenSignedAndApprovedProposalNeverUnlocksRunner() {
        val (verifier,p)=signed();val proposal=verifier.verify(p,1000)
        val queue=HumanReviewQueue(Files.createTempDirectory("evol-review-synthetic-").resolve("review.log"))
        val metadata=ProposalMetadata(proposal.id,proposal.diffSha256,proposal.originSha256,proposal.baseCommit,proposal.unifiedCommit,proposal.expiresAt)
        queue.register(metadata,1000);queue.decide(metadata.id,metadata.contentSha256,true,1001)
        val result=RestrictedTrialRunner().run(proposal)
        assertEquals("NOT_RUN",result.status);assertEquals("SANDBOX_NOT_QUALIFIED",result.reason)
    }
    @Test fun reviewAndRevocationPersistWithoutDiffsOrPrivateKeys() {
        val p=Files.createTempDirectory("evol-review-synthetic-").resolve("review.log");val m=metadata()
        val queue=HumanReviewQueue(p);queue.register(m,1000);queue.decide(m.id,m.contentSha256,true,1001)
        assertEquals(ReviewStatus.APPROVED_FOR_LAB,HumanReviewQueue(p).list(1002).single().status)
        queue.decide(m.id,m.contentSha256,false,1003)
        assertEquals(ReviewStatus.REJECTED,HumanReviewQueue(p).list(1004).single().status)
        val text=Files.readString(p);assertFalse(text.contains("fun answer"));assertFalse(text.contains("PRIVATE KEY"))
        assertEquals(ReviewStatus.EXPIRED,queue.list(2000).single().status)
    }
    @Test fun wrongContentAndApprovalReplayDoNotMutateJournal() {
        val path=Files.createTempDirectory("evol-review-synthetic-").resolve("review.log");val q=HumanReviewQueue(path);val m=metadata();q.register(m,1000)
        val pending=Files.readAllBytes(path)
        assertThrows(IllegalArgumentException::class.java){q.decide(m.id,"0".repeat(64),true,1001)}
        assertArrayEquals(pending,Files.readAllBytes(path));q.decide(m.id,m.contentSha256,true,1001)
        val approved=Files.readAllBytes(path)
        assertThrows(IllegalArgumentException::class.java){q.decide(m.id,m.contentSha256,true,1002)}
        assertArrayEquals(approved,Files.readAllBytes(path))
    }
    @Test fun truncatedReviewJournalIsRefusedWithoutResetOrAutomaticRecovery() {
        val p=Files.createTempDirectory("evol-review-synthetic-").resolve("review.log");val q=HumanReviewQueue(p);q.register(metadata(),1000)
        val bytes=Files.readAllBytes(p).dropLast(4).toByteArray();Files.write(p,bytes)
        assertThrows(IllegalArgumentException::class.java){q.list(1001)}
        assertArrayEquals(bytes,Files.readAllBytes(p))
    }
}
