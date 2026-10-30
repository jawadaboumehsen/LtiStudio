/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AcknowledgementsTest {

    @Test
    fun testValidation() {
        assertFailsWith<IllegalArgumentException> {
            Acknowledgement(featureId = "", acceptedAt = "2026-10-25T10:00:00Z")
        }
        assertFailsWith<IllegalArgumentException> {
            Acknowledgement(featureId = "   ", acceptedAt = "2026-10-25T10:00:00Z")
        }
        assertFailsWith<IllegalArgumentException> {
            Acknowledgement(featureId = "super_overlap", acceptedAt = "")
        }
        assertFailsWith<IllegalArgumentException> {
            Acknowledgement(featureId = "super_overlap", acceptedAt = "   ")
        }
    }

    @Test
    fun testEmptyAcknowledgements() {
        val acks = Acknowledgements.Empty
        assertEquals(0, acks.items.size)
        assertFalse(acks.isAcknowledged("super_overlap"))
    }

    @Test
    fun testIdempotentAccept() {
        var acks = Acknowledgements.Empty
        acks = acks.accept("super_overlap", "2026-10-25T10:00:00Z")
        assertTrue(acks.isAcknowledged("super_overlap"))
        assertEquals(1, acks.items.size)
        assertEquals("2026-10-25T10:00:00Z", acks.items.first().acceptedAt)

        // Accepting again with a different timestamp must NOT change the original or duplicate
        val secondAcks = acks.accept("super_overlap", "2026-10-25T12:00:00Z")
        assertEquals(1, secondAcks.items.size)
        assertEquals("2026-10-25T10:00:00Z", secondAcks.items.first().acceptedAt)

        // Accepting a different featureId appends
        val thirdAcks = secondAcks.accept("vabc_missing", "2026-10-25T11:00:00Z")
        assertEquals(2, thirdAcks.items.size)
        assertTrue(thirdAcks.isAcknowledged("vabc_missing"))
    }

    @Test
    fun testJsonCodecRoundTrip() {
        val acks = Acknowledgements(
            items = listOf(
                Acknowledgement("super_overlap", "2026-10-25T10:00:00Z"),
                Acknowledgement("vabc_missing", "2026-10-25T11:00:00Z"),
            ),
        )

        val json = acks.toJson()
        val decoded = Acknowledgements.fromJson(json)
        assertEquals(acks, decoded)
    }
}
