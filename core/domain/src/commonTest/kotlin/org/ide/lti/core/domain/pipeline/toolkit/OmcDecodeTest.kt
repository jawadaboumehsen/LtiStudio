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
import kotlin.test.assertTrue

class OmcDecodeTest {

    private val sampleCustomerXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <CustomerData>
          <GeneralInfo>
            <Country>USA</Country>
            <CountryISO>US</CountryISO>
            <Region>NA</Region>
            <SalesCode>ATT</SalesCode>
          </GeneralInfo>
          <Settings>
            <NetworkInfo>
              <MCC>310</MCC>
              <MNC>410</MNC>
              <NetworkName>AT&amp;T</NetworkName>
              <SPName>AT&amp;T</SPName>
            </NetworkInfo>
            <NetworkInfo>
              <MCC>310</MCC>
              <MNC>280</MNC>
              <NetworkName>AT&amp;T 5G</NetworkName>
            </NetworkInfo>
          </Settings>
        </CustomerData>
    """.trimIndent()

    private val sampleCscFeatureXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <FeatureSet>
          <CscFeature_VoiceCall_ConfigCallDurationDisplay>TRUE</CscFeature_VoiceCall_ConfigCallDurationDisplay>
          <CscFeature_SystemUI_ConfigOverrideDataIcon>5GE</CscFeature_SystemUI_ConfigOverrideDataIcon>
        </FeatureSet>
    """.trimIndent()

    @Test
    fun decodesSalesCodeAndCarrierNetworksFromCustomerXml() {
        val info = OmcDecode.decode(
            salesCodeText = "ATT\n",
            customerXmlText = sampleCustomerXml,
            cscFeatureXmlText = sampleCscFeatureXml,
        )

        assertEquals("ATT", info.salesCode)
        assertEquals("USA", info.country)
        assertEquals(2, info.networks.size)

        val net1 = info.networks[0]
        assertEquals("310", net1.mcc)
        assertEquals("410", net1.mnc)
        assertEquals("AT&T", net1.networkName)
        assertEquals("AT&T", net1.spName)

        val net2 = info.networks[1]
        assertEquals("310", net2.mcc)
        assertEquals("280", net2.mnc)
        assertEquals("AT&T 5G", net2.networkName)

        // Features
        assertEquals("TRUE", info.features["CscFeature_VoiceCall_ConfigCallDurationDisplay"])
        assertEquals("5GE", info.features["CscFeature_SystemUI_ConfigOverrideDataIcon"])
    }

    @Test
    fun handlesMissingOrEmptyInputsGracefully() {
        val info = OmcDecode.decode(
            salesCodeText = null,
            customerXmlText = null,
            cscFeatureXmlText = null,
        )

        assertEquals("UNKNOWN", info.salesCode)
        assertTrue(info.networks.isEmpty())
        assertTrue(info.features.isEmpty())
    }
}
