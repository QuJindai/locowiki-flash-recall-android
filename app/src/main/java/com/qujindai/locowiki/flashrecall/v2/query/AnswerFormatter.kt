package com.qujindai.locowiki.flashrecall.v2.query

import com.qujindai.locowiki.flashrecall.v2.domain.AnswerCard
import com.qujindai.locowiki.flashrecall.v2.domain.FactRecord
import com.qujindai.locowiki.flashrecall.v2.domain.ResultStatus

class AnswerFormatter {
    fun format(fact: FactRecord): AnswerCard {
        if (fact.status.equals("conflict", true)) {
            return AnswerCard(
                ResultStatus.CONFLICT,
                "当前记录存在冲突，不能作为确定答案。",
                evidence(fact),
            )
        }
        val prefix = if (fact.status.equals("verified", true)) "" else "未核实候选："
        val value = buildString {
            append(fact.valueText)
            if (fact.unit.isNotBlank()) append(fact.unit)
        }
        val year = fact.year?.let { "${it}年，" }.orEmpty()
        val sentence = when (fact.attributeKey) {
            "price" -> "$year${fact.canonicalName}的${fact.attributeName}为$value。"
            "quality_rate" -> "$year${fact.canonicalName}的${fact.attributeName}为$value。"
            "standard_number" -> "对应标准为${fact.valueText}，关联对象为${fact.canonicalName}。"
            "configuration" -> "${fact.canonicalName}的${fact.attributeName}：$value。"
            "fault" -> "${fact.canonicalName}的${fact.attributeName}：$value。"
            else -> "${fact.canonicalName}｜${fact.attributeName}：$value。"
        }
        val answer = buildString {
            append(prefix)
            append(sentence)
            if (fact.definitionText.isNotBlank()) append("口径：${fact.definitionText}。")
        }
        return AnswerCard(ResultStatus.FOUND, answer, evidence(fact))
    }

    private fun evidence(fact: FactRecord): String = buildList {
        if (fact.sourceName.isNotBlank()) add(fact.sourceName)
        if (fact.sourceLocation.isNotBlank()) add(fact.sourceLocation)
        if (fact.verifiedAt.isNotBlank()) add("核实于${fact.verifiedAt}")
        add("状态：${fact.status}")
    }.joinToString(" · ").ifBlank { "未提供来源定位" }
}
