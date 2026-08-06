package com.qujindai.locowiki.flashrecall.v2.speaker

enum class SpeakerIdentity {
    SELF_CONFIRMED,
    SELF_PROBABLE,
    OTHER_CONFIRMED,
    UNKNOWN,
}

class SpeakerDecisionPolicy(
    val selfThreshold: Float = 0.70f,
    val otherThreshold: Float = 0.52f,
) {
    init {
        require(selfThreshold > otherThreshold)
    }

    fun decide(hasSelfProfile: Boolean, similarity: Float): SpeakerIdentity {
        if (!hasSelfProfile || similarity.isNaN()) return SpeakerIdentity.UNKNOWN
        return when {
            similarity >= selfThreshold -> SpeakerIdentity.SELF_CONFIRMED
            similarity <= otherThreshold -> SpeakerIdentity.OTHER_CONFIRMED
            else -> SpeakerIdentity.UNKNOWN
        }
    }
}
