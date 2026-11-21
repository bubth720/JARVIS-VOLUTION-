package fr.jarvis.evolution.cli

import fr.jarvis.evolution.campaign.CampaignComparator
import fr.jarvis.evolution.campaign.CampaignEvidenceReader
import fr.jarvis.evolution.campaign.CampaignReport
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** `campaign-compare <before-dir> <before-sha> <after-dir> <after-sha> <new-report.json>` — read-only on both
 * evidence directories, writes only a NEW report file, executes nothing (F08, diagnostic and read-only). */
fun campaignMain(args: Array<String>) {
    require(args.size == 6 && args[0] == "campaign-compare") { "USAGE" }
    val target = Path.of(args[5]).toAbsolutePath()
    require(!Files.exists(target) && Files.isDirectory(target.parent)) { "NEW_REPORT_FILE_REQUIRED" }
    val comparison = CampaignComparator.compare(CampaignEvidenceReader.read(Path.of(args[1]), args[2]), CampaignEvidenceReader.read(Path.of(args[3]), args[4]))
    Files.writeString(target, CampaignReport.json(comparison) + "\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    println("{\"status\":\"${comparison.status}\",\"proposals\":${comparison.proposals.size},\"executionAuthorized\":false}")
}
