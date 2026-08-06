package com.qujindai.locowiki.flashrecall.v2.speaker

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerDecisionPolicyTest {
    private val policy = SpeakerDecisionPolicy(selfThreshold = 0.70f, otherThreshold = 0.52f)

    @Test fun highSimilarityIsSelf() = assertEquals(SpeakerIdentity.SELF_CONFIRMED, policy.decide(true, 0.81f))
    @Test fun middleSimilarityIsUnknown() = assertEquals(SpeakerIdentity.UNKNOWN, policy.decide(true, 0.60f))
    @Test fun lowSimilarityIsOther() = assertEquals(SpeakerIdentity.OTHER_CONFIRMED, policy.decide(true, 0.30f))
    @Test fun noProfileIsUnknown() = assertEquals(SpeakerIdentity.UNKNOWN, policy.decide(false, 0.99f))
}
