package com.qujindai.locowiki.flashrecall.v2.query

import com.qujindai.locowiki.flashrecall.v2.domain.MeetingContext
import com.qujindai.locowiki.flashrecall.v2.domain.QueryIntent
import java.util.Calendar

class QueryParser {
    private val yearPattern = Regex("(?<!\\d)(20\\d{2})(?:年)?")

    fun parse(question: String, context: MeetingContext, currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)): QueryIntent {
        val original = question.trim()
        val normalized = normalize(original)
        val requestedYear = when {
            yearPattern.containsMatchIn(normalized) -> yearPattern.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
            normalized.contains("前年") -> currentYear - 2
            normalized.contains("去年") || normalized.contains("上年度") -> currentYear - 1
            normalized.contains("今年") || normalized.contains("本年度") -> currentYear
            else -> context.year.takeIf { it in 2000..2100 }
        }
        val attribute = when {
            containsAny(normalized, "多少钱", "价格", "采购价", "合同价", "报价", "费用", "造价") -> "price"
            containsAny(normalized, "合格率", "通过率", "一次通过", "良率", "达成率") -> "quality_rate"
            containsAny(normalized, "标准号", "标准编号", "编号是什么", "哪个标准", "什么标准") -> "standard_number"
            containsAny(normalized, "配置", "有没有", "是否有", "搭载", "配备", "装了") -> "configuration"
            containsAny(normalized, "故障", "异常", "问题", "失效", "原因") -> "fault"
            else -> "general"
        }
        val hints = buildList {
            listOf(context.project, context.vehicle, context.factory, context.topic, context.competitor)
                .filter { it.isNotBlank() }
                .forEach(::add)
            Regex("[A-Za-z]+[-_]?\\d+[A-Za-z0-9_-]*|\\d+[A-Za-z]+[A-Za-z0-9_-]*")
                .findAll(original)
                .map { it.value }
                .forEach(::add)
        }.distinct()
        return QueryIntent(original, normalized, attribute, requestedYear, context, hints)
    }

    companion object {
        fun normalize(value: String): String = value.lowercase()
            .replace(Regex("[\\s，。！？、；：,.!?;:（）()【】\\[\\]‘’“”\"']+"), "")
            .trim()

        private fun containsAny(text: String, vararg values: String) = values.any(text::contains)
    }
}
