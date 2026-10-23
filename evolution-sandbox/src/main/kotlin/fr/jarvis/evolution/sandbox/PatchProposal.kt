package fr.jarvis.evolution.sandbox

import java.security.PublicKey
import java.security.Signature
import java.util.UUID

data class PatchProposal(val version:Int,val mission:String,val baseCommit:String,val unifiedCommit:String,
    val diff:String,val diffSha256:String,val originSha256:String,val expiresAt:Long,val signature:ByteArray) {
    fun signingBytes()="jarvis.patch-proposal/$version\n$mission\n$baseCommit\n$unifiedCommit\n$diffSha256\n$originSha256\n$expiresAt\n".toByteArray(Charsets.UTF_8)
}
class VerifiedProposal internal constructor(val id:String,val diffSha256:String,val originSha256:String,
    val baseCommit:String,val unifiedCommit:String,val expiresAt:Long,files:List<String>) {
    val files:List<String> = java.util.List.copyOf(files)
}
/** Host-pinned origins only. A valid origin signature is never a human approval or execution permission. */
class ProposalVerifier(private val policy:DiffPolicyValidator,trustedOrigins:Map<String,PublicKey>,private val unifiedCommit:String,private val expectedBaseCommit:String) {
    private val trusted=trustedOrigins.toMap()
    init { require(unifiedCommit.matches(Regex("[0-9a-f]{40}")) && expectedBaseCommit.matches(Regex("[0-9a-f]{40}")));require(trusted.size in 1..16)
        require(trusted.all{(hash,key)->hash==DiffPolicyValidator.sha256(key.encoded)}) }
    fun verify(proposal:PatchProposal,now:Long):VerifiedProposal {
        require(now>=0 && proposal.version==1 && proposal.mission=="EVOL-03")
        require(proposal.baseCommit==expectedBaseCommit && proposal.unifiedCommit==unifiedCommit)
        require(proposal.expiresAt>now && proposal.expiresAt-now<=86_400_000)
        val summary=policy.validate(proposal.diff);require(summary.sha256==proposal.diffSha256)
        val key=requireNotNull(trusted[proposal.originSha256]){"UNTRUSTED_ORIGIN"}
        require(proposal.signature.size==64)
        require(Signature.getInstance("Ed25519").run{initVerify(key);update(proposal.signingBytes());verify(proposal.signature.copyOf())}){"INVALID_SIGNATURE"}
        return VerifiedProposal(UUID.randomUUID().toString(),summary.sha256,proposal.originSha256,proposal.baseCommit,unifiedCommit,proposal.expiresAt,summary.files)
    }
}
data class TrialRefusal(val status:String="NOT_RUN",val reason:String="SANDBOX_NOT_QUALIFIED",
    val diagnostic:String="bwrap: setting up uid map: Read-only file system")
/** No ProcessBuilder, Runtime.exec, Git apply or unlock flag is provided by this unqualified tranche. */
class RestrictedTrialRunner {
    fun run(@Suppress("UNUSED_PARAMETER") proposal:VerifiedProposal):TrialRefusal = TrialRefusal()
}
