package fr.jarvis.evolution.review

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

enum class ReviewStatus { PENDING, APPROVED_FOR_LAB, REJECTED, EXPIRED }
data class ProposalMetadata(val id:String,val contentSha256:String,val originSha256:String,val baseCommit:String,
    val unifiedCommit:String,val expiresAt:Long) {
    init { require(UUID.fromString(id).toString()==id)
        require(listOf(contentSha256,originSha256).all{it.matches(Regex("[0-9a-f]{64}"))})
        require(listOf(baseCommit,unifiedCommit).all{it.matches(Regex("[0-9a-f]{40}"))} && expiresAt>=0) }
}
data class ReviewEntry(val metadata:ProposalMetadata,val status:ReviewStatus)
/** Durable, bounded, hash-chained metadata only. Host/UI decisions do not authorize execution. */
class HumanReviewQueue(private val path:Path) {
    private val header="jarvis.review-journal/1\n"
    init {
        val absolute=path.toAbsolutePath().normalize();var p:Path?=absolute
        while(p!=null){require(!Files.isSymbolicLink(p));p=p.parent}
        require(Files.isDirectory(absolute.parent))
    }
    private fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    private data class Journal(val entries:LinkedHashMap<String,ReviewEntry>,val previousHash:String,val records:Int)
    private fun parse(bytes:ByteArray):Journal {
        require(bytes.size<=1024*1024)
        if(bytes.isEmpty())return Journal(linkedMapOf(),hash(header),0)
        val text=bytes.toString(Charsets.US_ASCII)
        require(text.startsWith(header) && text.endsWith('\n')){"CORRUPT_REVIEW_JOURNAL"}
        val entries=linkedMapOf<String,ReviewEntry>();var previous=hash(header);var count=0
        for(line in text.removePrefix(header).split('\n').dropLast(1)) {
            require(++count<=4096)
            val parts=line.split('|');require(parts.size==2 && hash(previous+"\n"+parts[0])==parts[1]){"CORRUPT_REVIEW_CHAIN"};previous=parts[1]
            val p=parts[0].split(',')
            when(p[0]) {
                "REGISTER"->{
                    require(p.size==8 && entries.size<128)
                    val metadata=ProposalMetadata(p[1],p[2],p[3],p[4],p[5],p[6].toLong())
                    require(p[7].toLong()>=0 && metadata.id !in entries)
                    entries[metadata.id]=ReviewEntry(metadata,ReviewStatus.PENDING)
                }
                "DECIDE"->{
                    require(p.size==5 && p[4].toLong()>=0)
                    val old=requireNotNull(entries[p[1]]);require(p[3]==old.metadata.contentSha256)
                    val next=ReviewStatus.valueOf(p[2]);validateTransition(old.status,next)
                    entries[p[1]]=old.copy(status=next)
                }
                else->error("UNKNOWN_REVIEW_RECORD")
            }
        }
        return Journal(entries,previous,count)
    }
    private fun validateTransition(previous:ReviewStatus,next:ReviewStatus) {
        require(next==ReviewStatus.APPROVED_FOR_LAB && previous==ReviewStatus.PENDING ||
            next==ReviewStatus.REJECTED && previous in setOf(ReviewStatus.PENDING,ReviewStatus.APPROVED_FOR_LAB))
    }
    private fun <T> locked(block:(FileChannel,Journal)->T):T {
        FileChannel.open(path,StandardOpenOption.READ,StandardOpenOption.WRITE,StandardOpenOption.CREATE,LinkOption.NOFOLLOW_LINKS).use {channel ->
            channel.lock().use {
                require(channel.size()<=1024*1024)
                val bytes=ByteBuffer.allocate(channel.size().toInt());channel.position(0)
                while(bytes.hasRemaining())require(channel.read(bytes)>0)
                return block(channel,parse(bytes.array()))
            }
        }
    }
    private fun append(channel:FileChannel,journal:Journal,record:String) {
        require(journal.records<4096)
        val line=record+"|"+hash(journal.previousHash+"\n"+record)+"\n"
        val data=(if(channel.size()==0L)header+line else line).toByteArray(Charsets.US_ASCII)
        require(channel.size()+data.size<=1024*1024)
        channel.position(channel.size());val buffer=ByteBuffer.wrap(data)
        while(buffer.hasRemaining())require(channel.write(buffer)>0)
        channel.force(true)
    }
    fun register(metadata:ProposalMetadata,now:Long) = locked {channel,journal ->
        require(now>=0 && metadata.expiresAt>now)
        val existing=journal.entries[metadata.id]
        if(existing!=null){require(existing.metadata==metadata);return@locked existing}
        require(journal.entries.size<128)
        append(channel,journal,"REGISTER,${metadata.id},${metadata.contentSha256},${metadata.originSha256},${metadata.baseCommit},${metadata.unifiedCommit},${metadata.expiresAt},$now")
        ReviewEntry(metadata,ReviewStatus.PENDING)
    }
    /** Caller must be a trusted host that displayed and confirmed the exact diff bound to this digest. */
    fun decide(id:String,exactDigest:String,approved:Boolean,now:Long) = locked {channel,journal ->
        val old=requireNotNull(journal.entries[id]);require(now>=0 && now<old.metadata.expiresAt && exactDigest==old.metadata.contentSha256)
        val next=if(approved)ReviewStatus.APPROVED_FOR_LAB else ReviewStatus.REJECTED
        validateTransition(old.status,next)
        append(channel,journal,"DECIDE,$id,${next.name},$exactDigest,$now")
        old.copy(status=next)
    }
    fun list(now:Long):List<ReviewEntry> = locked {_,journal ->
        require(now>=0)
        journal.entries.values.map {if(now>=it.metadata.expiresAt)it.copy(status=ReviewStatus.EXPIRED)else it}
    }
}
