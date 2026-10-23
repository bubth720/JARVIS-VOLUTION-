package fr.jarvis.evolution.sandbox

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

class DiffPolicyException(val reason: String):IllegalArgumentException(reason)
data class DiffSummary(val sha256:String,val files:List<String>,val added:Int,val removed:Int)
/** Text-only replacement hunks on explicitly named regular source files; no patch is applied. */
class DiffPolicyValidator(allowedFiles:Set<String>) {
    private val allowed=allowedFiles.toSet()
    init { require(allowed.size in 1..8 && allowed.all(::safePath)) }
    companion object {
        fun safePath(path:String):Boolean {
            if(path.length !in 1..180 || !path.matches(Regex("[A-Za-z0-9_./-]+")))return false
            val parts=path.split('/')
            if(parts.any{it.isEmpty()||it=="."||it==".."||it.startsWith('.')})return false
            if(!path.startsWith("src/"))return false
            return path.endsWith(".kt")||path.endsWith(".java")
        }
        fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    }
    fun validate(diff:String):DiffSummary {
        fun checked(ok:Boolean,reason:String){if(!ok)throw DiffPolicyException(reason)}
        checked(diff.length<=65536,"DIFF_SIZE")
        val encoded=try{Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(diff))}catch(_:Exception){throw DiffPolicyException("UTF8")}
        val bytes=ByteArray(encoded.remaining()).also{encoded.get(it)};checked(bytes.size<=65536,"DIFF_SIZE")
        checked(diff.endsWith('\n') && diff.none{it=='\r'||it=='\u0000'||it.code<32 && it!='\n' && it!='\t'},"CONTROL_CHARACTERS")
        val lines=diff.dropLast(1).split('\n');checked(lines.size<=4096,"LINE_LIMIT")
        val files=mutableListOf<String>();var i=0;var added=0;var removed=0
        while(i<lines.size) {
            val h=Regex("diff --git a/([A-Za-z0-9_./-]+) b/([A-Za-z0-9_./-]+)").matchEntire(lines[i++])?:throw DiffPolicyException("HEADER")
            val name=h.groupValues[1];checked(name==h.groupValues[2] && safePath(name) && name in allowed,"PATH_NOT_ALLOWED")
            checked(name !in files && files.size<8,"DUPLICATE_OR_FILE_LIMIT");files+=name
            checked(i<lines.size && lines[i++].matches(Regex("index [0-9a-f]{7,40}\\.\\.[0-9a-f]{7,40} 100644")),"MODE_OR_INDEX")
            checked(i<lines.size && lines[i++]=="--- a/$name","SOURCE_HEADER")
            checked(i<lines.size && lines[i++]=="+++ b/$name","TARGET_HEADER")
            var hunks=0;var previousOldEnd=0;var previousNewEnd=0
            while(i<lines.size && !lines[i].startsWith("diff --git ")) {
                val m=Regex("@@ -([0-9]+)(?:,([0-9]+))? \\+([0-9]+)(?:,([0-9]+))? @@(?: [^\\r\\n]*)?").matchEntire(lines[i++])?:throw DiffPolicyException("HUNK_HEADER")
                val oldStart=m.groupValues[1].toIntOrNull()?:throw DiffPolicyException("HUNK_RANGE")
                val newStart=m.groupValues[3].toIntOrNull()?:throw DiffPolicyException("HUNK_RANGE")
                var old=m.groupValues[2].ifEmpty{"1"}.toIntOrNull()?:throw DiffPolicyException("HUNK_RANGE")
                var fresh=m.groupValues[4].ifEmpty{"1"}.toIntOrNull()?:throw DiffPolicyException("HUNK_RANGE")
                checked(old in 1..4096 && fresh in 1..4096 && oldStart in 1..1_000_000 && newStart in 1..1_000_000,"HUNK_RANGE")
                checked(oldStart>=previousOldEnd && newStart>=previousNewEnd,"HUNK_OVERLAP")
                previousOldEnd=oldStart+old;previousNewEnd=newStart+fresh;hunks++
                while(old>0 || fresh>0) {
                    checked(i<lines.size,"TRUNCATED_HUNK")
                    val line=lines[i++];checked(line.isNotEmpty(),"HUNK_PREFIX")
                    when(line[0]) {
                        ' '->{old--;fresh--}
                        '-'->{old--;removed++}
                        '+'->{fresh--;added++}
                        else->throw DiffPolicyException("HUNK_PREFIX")
                    }
                    checked(old>=0 && fresh>=0,"HUNK_COUNTS")
                }
            }
            checked(hunks>0,"NO_HUNK")
        }
        checked(files.isNotEmpty() && added+removed>0,"NO_CHANGE")
        return DiffSummary(sha256(bytes),files.toList(),added,removed)
    }
    /** Read-only validation of only declared files in a synthetic, explicitly marked lab root. */
    fun validateSyntheticFiles(root:Path,summary:DiffSummary) {
        val absolute=root.toAbsolutePath().normalize()
        require(Files.isDirectory(absolute,LinkOption.NOFOLLOW_LINKS))
        var parent:Path?=absolute
        while(parent!=null){require(!Files.isSymbolicLink(parent));parent=parent.parent}
        val marker=absolute.resolve(".jarvis-synthetic-workspace")
        require(!Files.isSymbolicLink(marker) && Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS))
        require(Files.size(marker)<=64 && Files.readString(marker)=="EVOL-03-SYNTHETIC\n")
        for(file in summary.files) {
            require(file in allowed && safePath(file))
            val target=absolute.resolve(file).normalize();require(target.startsWith(absolute))
            var part=absolute
            for(component in absolute.relativize(target)){part=part.resolve(component);require(!Files.isSymbolicLink(part))}
            require(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS) && Files.size(target)<=65536)
        }
    }
}
