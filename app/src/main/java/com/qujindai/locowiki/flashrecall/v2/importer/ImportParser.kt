package com.qujindai.locowiki.flashrecall.v2.importer

import com.qujindai.locowiki.flashrecall.v2.domain.ImportFact
import org.json.JSONArray
import org.json.JSONObject

object ImportParser {
    fun parseJson(json: String): List<ImportFact> {
        val array = JSONArray(json)
        return List(array.length()) { index -> fromJson(array.getJSONObject(index)) }
    }

    fun parseCsv(csv: String): List<ImportFact> {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.isNotEmpty()) { "CSV缺少表头" }
        val headers = parseCsvRow(lines.first()).map(String::trim)
        val index = headers.withIndex().associate { it.value to it.index }
        listOf("entity_name", "attribute_name", "value_text").forEach { require(index.containsKey(it)) { "CSV缺少字段：$it" } }
        return lines.drop(1).mapIndexed { rowIndex, line ->
            val values = parseCsvRow(line)
            require(values.size == headers.size) { "CSV第${rowIndex + 2}行字段数量不一致" }
            fun get(name: String): String = index[name]?.let { values[it].trim() }.orEmpty()
            ImportFact(
                entityType = get("entity_type").ifBlank { "general" },
                canonicalName = get("entity_name").required("entity_name"),
                aliases = splitAliases(get("aliases")),
                attributeKey = get("attribute_key").ifBlank { "general" },
                attributeName = get("attribute_name").required("attribute_name"),
                valueText = get("value_text").required("value_text"),
                valueNumber = get("value_number").toDoubleOrNull() ?: get("value_text").toDoubleOrNull(),
                unit = get("unit"),
                year = get("year").toIntOrNull(),
                project = get("project"),
                factory = get("factory"),
                topic = get("topic"),
                definitionText = get("definition_text"),
                status = get("status").ifBlank { "draft" },
                sourceName = get("source_name").ifBlank { "导入文件" },
                sourceLocation = get("source_location"),
                verifiedAt = get("verified_at"),
            )
        }
    }

    private fun fromJson(o: JSONObject) = ImportFact(
        entityType = o.optString("entity_type", "general"),
        canonicalName = o.optString("entity_name").required("entity_name"),
        aliases = splitAliases(o.optString("aliases")),
        attributeKey = o.optString("attribute_key", "general"),
        attributeName = o.optString("attribute_name").required("attribute_name"),
        valueText = o.optString("value_text").required("value_text"),
        valueNumber = if (o.has("value_number") && !o.isNull("value_number")) o.optDouble("value_number") else o.optString("value_text").toDoubleOrNull(),
        unit = o.optString("unit"),
        year = if (o.has("year") && !o.isNull("year")) o.optInt("year") else null,
        project = o.optString("project"),
        factory = o.optString("factory"),
        topic = o.optString("topic"),
        definitionText = o.optString("definition_text"),
        status = o.optString("status", "draft").ifBlank { "draft" },
        sourceName = o.optString("source_name", "导入文件"),
        sourceLocation = o.optString("source_location"),
        verifiedAt = o.optString("verified_at"),
    )

    internal fun parseCsvRow(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            when (val c = line[i]) {
                '"' -> if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"'); i++
                } else quoted = !quoted
                ',' -> if (quoted) current.append(c) else { out += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        require(!quoted) { "CSV引号未闭合" }
        out += current.toString()
        return out
    }

    private fun splitAliases(value: String) = value.split('|', '；', ';').map(String::trim).filter(String::isNotBlank).distinct()
    private fun String.required(name: String): String = trim().also { require(it.isNotEmpty()) { "缺少必填字段：$name" } }
}
