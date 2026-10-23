package fr.jarvis.evolution.cli

import fr.jarvis.evolution.sandbox.*
import fr.jarvis.evolution.review.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.Signature

/** Trusted built-in synthetic text only. The diff is data: no patch/build/process is executed. */
fun proposalPolicyDemo(args:Array<String>) {
    require(args.size==2)
    val root=Path.of(args[1]);require(!Files.exists(root));Files.createDirectories(root.resolve("src"))
    Files.writeString(root.resolve(".jarvis-synthetic-workspace"),"EVOL-03-SYNTHETIC\n")
    val target=root.resolve("src/Synthetic.kt");val original="fun answer() = 1\n";Files.writeString(target,original)
    val diff="diff --git a/src/Synthetic.kt b/src/Synthetic.kt\nindex 1111111..2222222 100644\n--- a/src/Synthetic.kt\n+++ b/src/Synthetic.kt\n@@ -1 +1 @@\n-fun answer() = 1\n+fun answer() = 2\n"
    val policy=DiffPolicyValidator(setOf("src/Synthetic.kt"));val summary=policy.validate(diff)
    policy.validateSyntheticFiles(root,summary)
    val keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair() // In RAM only, never written/exported.
    val origin=DiffPolicyValidator.sha256(keys.public.encoded)
    val base="a".repeat(40) // Synthetic reference, not a claim of an existing Git commit.
    val unified="bd283dad3a57effa276f7f4441901839d2834d5c"
    val unsigned=PatchProposal(1,"EVOL-03",base,unified,diff,summary.sha256,origin,2000,byteArrayOf())
    val signature=Signature.getInstance("Ed25519").run{initSign(keys.private);update(unsigned.signingBytes());sign()}
    val verified=ProposalVerifier(policy,mapOf(origin to keys.public),unified,base).verify(unsigned.copy(signature=signature),1000)
    val queue=HumanReviewQueue(root.resolve("review.log"))
    val metadata=ProposalMetadata(verified.id,verified.diffSha256,verified.originSha256,verified.baseCommit,verified.unifiedCommit,verified.expiresAt)
    queue.register(metadata,1000);queue.decide(metadata.id,metadata.contentSha256,true,1001)
    check(HumanReviewQueue(root.resolve("review.log")).list(1002).single().status==ReviewStatus.APPROVED_FOR_LAB)
    val result=RestrictedTrialRunner().run(verified)
    check(Files.readString(target)==original && result.status=="NOT_RUN")
    println("{\"policyValidation\":\"PASS\",\"originSignature\":\"ED25519_VERIFIED_SYNTHETIC\",\"reviewPersisted\":true,\"runner\":\"NOT_RUN\",\"reason\":\"SANDBOX_NOT_QUALIFIED\",\"sourceUnchanged\":true,\"gitBaseQualified\":false}")
}
