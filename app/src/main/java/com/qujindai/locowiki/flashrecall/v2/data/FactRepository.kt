package com.qujindai.locowiki.flashrecall.v2.data

import android.content.Context
import android.os.PowerManager
import com.qujindai.locowiki.flashrecall.v2.domain.*
import com.qujindai.locowiki.flashrecall.v2.importer.ImportParser
import com.qujindai.locowiki.flashrecall.v2.query.AnswerFormatter
import com.qujindai.locowiki.flashrecall.v2.query.QueryParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.ceil

class FactRepository(private val context: Context, private val db: AppDatabase = AppDatabase.get(context)) {
    private val aliasMutex = Mutex()
    @Volatile private var aliasIndex: Map<String, Long> = emptyMap()
    private val parser = QueryParser()
    private val formatter = AnswerFormatter()

    suspend fun ensureSeeded() {
        if (db.factDao().count() > 0) {
            refreshAliasIndex()
            return
        }
        val json = context.assets.open("seed_facts.json").bufferedReader().use { it.readText() }
        val facts = ImportParser.parseJson(json)
        applyImport("seed_facts.json", facts, replaceAll = true)
    }

    suspend fun resetSeed() {
        db.ftsDao().deleteAll()
        db.factDao().deleteAll()
        db.entityDao().deleteAll()
        val json = context.assets.open("seed_facts.json").bufferedReader().use { it.readText() }
        applyImport("seed_facts.json", ImportParser.parseJson(json), replaceAll = false)
    }

    suspend fun search(question: String, meetingContext: MeetingContext): AnswerCard {
        val parseStart = System.nanoTime()
        val intent = parser.parse(question, meetingContext)
        val parseMs = elapsedMs(parseStart)

        val matchedEntityIds = aliasIndex.entries
            .asSequence()
            .filter { (alias, _) -> alias.length >= 2 && intent.normalized.contains(alias) }
            .map { it.value }
            .toSet()

        val exactStart = System.nanoTime()
        val exactRows = db.factDao().exactCandidates(
            intent.attributeKey,
            intent.requestedYear,
            intent.context.project.trim(),
            intent.context.factory.trim(),
            intent.context.topic.trim(),
        )
        val exactMs = elapsedMs(exactStart)
        val exactScored = scoreRows(intent, exactRows, matchedEntityIds)
        if (exactScored.isNotEmpty() && exactScored.first().second >= 65) {
            return formatScored(intent, exactScored, parseMs, exactMs, 0, "exact")
        }

        val ftsStart = System.nanoTime()
        val ftsQuery = buildFtsQuery(intent)
        val ids = if (ftsQuery.isBlank()) emptyList() else runCatching { db.ftsDao().searchIds(ftsQuery) }.getOrDefault(emptyList())
        val ftsRows = if (ids.isEmpty()) emptyList() else db.factDao().byIds(ids)
        val ftsMs = elapsedMs(ftsStart)
        val ftsScored = scoreRows(intent, ftsRows, matchedEntityIds)
        return if (ftsScored.isEmpty() || ftsScored.first().second < 55) {
            AnswerCard(
                status = ResultStatus.NOT_FOUND,
                answer = "当前本地知识库没有可确认的记录。",
                evidence = "未命中已核实事实",
                timing = QueryStageTiming(parseMs, exactMs, ftsMs, 0, "not_found"),
            )
        } else {
            formatScored(intent, ftsScored, parseMs, exactMs, ftsMs, "fts5")
        }
    }

    private fun formatScored(
        intent: QueryIntent,
        scored: List<Pair<FactRecord, Int>>,
        parseMs: Long,
        exactMs: Long,
        ftsMs: Long,
        route: String,
    ): AnswerCard {
        val best = scored.first()
        val close = scored.drop(1).filter { best.second - it.second <= 7 && it.second >= 55 }.take(3)
        if (close.isNotEmpty()) {
            val candidates = (listOf(best) + close).map { "${it.first.canonicalName}｜${it.first.attributeName}｜${it.first.year ?: "未标年"}" }
            return AnswerCard(
                ResultStatus.AMBIGUOUS,
                "找到多个接近结果，请补充对象、工厂或年份。",
                "候选数量：${candidates.size}",
                candidates,
                QueryStageTiming(parseMs, exactMs, ftsMs, 0, route),
            )
        }
        val compileStart = System.nanoTime()
        val answer = formatter.format(best.first)
        val compileMs = elapsedMs(compileStart)
        return answer.copy(timing = QueryStageTiming(parseMs, exactMs, ftsMs, compileMs, route))
    }

    private suspend fun scoreRows(intent: QueryIntent, rows: List<FactJoinedRow>, matchedEntityIds: Set<Long>): List<Pair<FactRecord, Int>> {
        val aliasesByEntity = rows.map { it.entityId }.distinct().associateWith { db.aliasDao().aliasesFor(it) }
        return rows.map { row ->
            val record = row.toRecord(aliasesByEntity[row.entityId].orEmpty())
            record to score(intent, record, matchedEntityIds)
        }.sortedByDescending { it.second }
    }

    private fun score(intent: QueryIntent, fact: FactRecord, matchedEntityIds: Set<Long>): Int {
        var score = when (fact.status.lowercase()) {
            "verified" -> 12
            "draft" -> -15
            "conflict" -> -35
            "expired" -> -70
            else -> -20
        }
        val q = intent.normalized
        if (fact.entityId in matchedEntityIds) score += 65
        val names = listOf(fact.canonicalName) + fact.aliases
        if (names.any { normalize(it).length >= 2 && q.contains(normalize(it)) }) score += 70
        if (intent.entityHints.any { hint -> names.any { related(hint, it) } }) score += 30
        if (intent.attributeKey != "general") score += if (intent.attributeKey == fact.attributeKey) 35 else -25
        if (intent.requestedYear != null && fact.year != null) score += if (intent.requestedYear == fact.year) 20 else -30
        score += contextScore(intent.context.project, fact.project, 18)
        score += contextScore(intent.context.factory, fact.factory, 14)
        score += contextScore(intent.context.topic, fact.topic, 14)
        if (fact.project.isNotBlank() && q.contains(normalize(fact.project))) score += 16
        if (fact.factory.isNotBlank() && q.contains(normalize(fact.factory))) score += 12
        return score
    }

    private fun buildFtsQuery(intent: QueryIntent): String {
        val tokens = buildList {
            addAll(intent.entityHints)
            if (intent.context.project.isNotBlank()) add(intent.context.project)
            if (intent.context.factory.isNotBlank()) add(intent.context.factory)
            if (intent.context.topic.isNotBlank()) add(intent.context.topic)
        }.map(::normalize).filter { it.length >= 2 }.distinct().take(6)
        return tokens.joinToString(" OR ") { "\"${it.replace("\"", "") }\"" }
    }

    suspend fun previewImport(fileName: String, content: String): ImportPreview {
        val parsed = when {
            fileName.lowercase().endsWith(".csv") -> ImportParser.parseCsv(content)
            fileName.lowercase().endsWith(".json") -> ImportParser.parseJson(content)
            else -> throw IllegalArgumentException("仅支持 UTF-8 JSON 或 CSV")
        }
        var newCount = 0
        var updateCount = 0
        var conflictCount = 0
        for (fact in parsed) {
            val entity = db.entityDao().find(fact.canonicalName, fact.entityType)
            if (entity == null) {
                newCount++
                continue
            }
            val existing = db.factDao().findExisting(entity.entityId, fact.attributeKey, fact.year, fact.project, fact.factory, fact.topic)
            when {
                existing == null -> newCount++
                existing.valueText == fact.valueText && existing.unit == fact.unit -> updateCount++
                else -> conflictCount++
            }
        }
        return ImportPreview(fileName, parsed, newCount, updateCount, conflictCount, 0, emptyList())
    }

    suspend fun confirmImport(preview: ImportPreview): Long = applyImport(preview.fileName, preview.facts, replaceAll = false)

    private suspend fun applyImport(fileName: String, facts: List<ImportFact>, replaceAll: Boolean): Long {
        var newCount = 0
        var updateCount = 0
        var conflictCount = 0
        for (item in facts) {
            val existingEntity = db.entityDao().find(item.canonicalName, item.entityType)
            val entityId = existingEntity?.entityId ?: db.entityDao().insert(EntityEntity(canonicalName = item.canonicalName, entityType = item.entityType)).also { newCount++ }
            val allAliases = (item.aliases + item.canonicalName).map(String::trim).filter(String::isNotBlank).distinct()
            allAliases.forEachIndexed { index, alias ->
                db.aliasDao().insert(AliasEntity(entityId = entityId, aliasText = alias, aliasType = if (alias == item.canonicalName) "canonical" else "spoken", priority = 100 - index))
            }
            val source = db.sourceDao().find(item.sourceName, item.sourceLocation)
            val sourceId = source?.sourceId ?: db.sourceDao().insert(SourceEntity(sourceName = item.sourceName.ifBlank { "未提供来源" }, sourceLocation = item.sourceLocation))
            val existing = db.factDao().findExisting(entityId, item.attributeKey, item.year, item.project, item.factory, item.topic)
            val status = when {
                existing == null -> item.status.ifBlank { "draft" }
                existing.valueText == item.valueText && existing.unit == item.unit -> item.status.ifBlank { existing.status }
                else -> "conflict"
            }
            if (existing != null) {
                if (status == "conflict") conflictCount++ else updateCount++
            }
            val fact = FactEntity(
                factId = existing?.factId ?: 0,
                entityId = entityId,
                attributeKey = item.attributeKey.ifBlank { "general" },
                attributeName = item.attributeName,
                valueText = item.valueText,
                valueNumber = item.valueNumber,
                unit = item.unit,
                year = item.year,
                project = item.project,
                factory = item.factory,
                topic = item.topic,
                definitionText = item.definitionText,
                status = status,
                sourceId = sourceId,
                verifiedAt = item.verifiedAt,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            val factId = db.factDao().upsert(fact)
            db.ftsDao().upsert(FactSearchFts(
                rowId = factId,
                canonicalName = item.canonicalName,
                aliases = allAliases.joinToString(" "),
                attributeName = item.attributeName,
                valueText = item.valueText,
                project = item.project,
                factory = item.factory,
                topic = item.topic,
                sourceName = item.sourceName,
            ))
        }
        refreshAliasIndex()
        val digest = sha256(facts.joinToString("|") { it.toString() })
        return db.importBatchDao().insert(ImportBatchEntity(
            fileName = fileName,
            fileHash = digest,
            recordCount = facts.size,
            newCount = newCount,
            updateCount = updateCount,
            conflictCount = conflictCount,
            rejectedCount = 0,
            status = "confirmed",
        ))
    }

    suspend fun refreshAliasIndex() = aliasMutex.withLock {
        aliasIndex = db.aliasDao().allEnabled().associate { normalize(it.aliasText) to it.entityId }
    }


    suspend fun hotwordsText(): String {
        val aliases = db.aliasDao().allEnabled()
            .sortedWith(compareByDescending<AliasEntity> { it.priority }.thenBy { it.aliasText })
            .map { it.aliasText.trim() }
            .filter { it.length >= 2 }
            .distinct()
        return aliases.joinToString("\n")
    }

    suspend fun dataStatus(): DataStatus {
        val values = db.latencyDao().latest(500).map { it.endToEndMs }.filter { it > 0 }.sorted()
        fun percentile(p: Double): Long = if (values.isEmpty()) 0 else values[(ceil((values.size - 1) * p)).toInt().coerceIn(values.indices)]
        return DataStatus(
            entities = db.entityDao().count(),
            aliases = db.aliasDao().count(),
            facts = db.factDao().count(),
            verified = db.factDao().countByStatus("verified"),
            drafts = db.factDao().countByStatus("draft"),
            conflicts = db.factDao().countByStatus("conflict"),
            latencySamples = values.size,
            p50Ms = percentile(0.50),
            p95Ms = percentile(0.95),
            meetings = db.meetingSessionDao().count(),
            utterances = db.utteranceDao().count(),
            archivedQueries = db.queryRecordDao().count(),
        )
    }

    suspend fun logLatency(
        question: String,
        answer: AnswerCard,
        asrTiming: AsrTiming,
        endToEndMs: Long,
        thermalStatus: Int,
        coldOrWarm: String,
        sessionId: String? = null,
    ) {
        val status = dataStatus()
        db.latencyDao().insert(LatencyTraceEntity(
            sessionId = sessionId ?: UUID.randomUUID().toString(),
            queryText = question,
            modelName = "streaming-zipformer-small-bilingual-zh-en-2023-02-16",
            modelPrecision = "int8-mixed",
            threadCount = Runtime.getRuntime().availableProcessors().coerceAtMost(4),
            factCount = status.facts,
            aliasCount = status.aliases,
            thermalStatus = thermalStatus,
            coldOrWarm = coldOrWarm,
            queryRoute = answer.timing.route,
            speechStartNs = asrTiming.speechStartNs,
            speechEndNs = asrTiming.speechEndNs,
            asrFirstPartialNs = asrTiming.firstPartialNs,
            asrFinalNs = asrTiming.finalNs,
            asrFirstPartialMs = asrTiming.firstPartialMs,
            asrEndpointMs = asrTiming.endpointMs,
            queryParseMs = answer.timing.parseMs,
            exactLookupMs = answer.timing.exactLookupMs,
            ftsMs = answer.timing.ftsMs,
            answerCompileMs = answer.timing.answerCompileMs,
            endToEndMs = endToEndMs,
            resultStatus = answer.status.name,
        ))
    }

    suspend fun exportLatencyCsv(): String {
        val rows = db.latencyDao().latest(5000)
        return buildString {
            appendLine("created_at,query_text,result_status,query_route,asr_first_partial_ms,asr_endpoint_ms,query_parse_ms,exact_lookup_ms,fts_ms,answer_compile_ms,end_to_end_ms,thermal_status")
            rows.reversed().forEach { row ->
                appendLine(listOf(
                    row.createdAt, csv(row.queryText), row.resultStatus, row.queryRoute,
                    row.asrFirstPartialMs, row.asrEndpointMs, row.queryParseMs,
                    row.exactLookupMs, row.ftsMs, row.answerCompileMs,
                    row.endToEndMs, row.thermalStatus,
                ).joinToString(","))
            }
        }
    }

    private fun FactJoinedRow.toRecord(aliases: List<String>) = FactRecord(
        factId, entityId, canonicalName, entityType, aliases, attributeKey, attributeName,
        valueText, valueNumber, unit, year, project, factory, topic, definitionText,
        status, sourceName, sourceLocation, verifiedAt,
    )

    private fun elapsedMs(startNs: Long) = (System.nanoTime() - startNs) / 1_000_000
    private fun normalize(value: String) = value.lowercase().replace(Regex("[\\s，。！？、；：,.!?;:（）()【】\\[\\]‘’“”\"']+"), "")
    private fun related(a: String, b: String): Boolean { val x = normalize(a); val y = normalize(b); return x.isNotBlank() && y.isNotBlank() && (x.contains(y) || y.contains(x)) }
    private fun contextScore(context: String, value: String, weight: Int) = when {
        context.isBlank() -> 0
        value.isBlank() -> -2
        related(context, value) -> weight
        else -> -8
    }
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
}
