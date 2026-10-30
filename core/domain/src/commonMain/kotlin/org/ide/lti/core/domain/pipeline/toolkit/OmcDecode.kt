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

import kotlinx.serialization.Serializable

/**
 * Decoded carrier and network configuration details (FR-032).
 */
@Serializable
public data class CarrierNetwork(
    public val mcc: String,
    public val mnc: String,
    public val networkName: String,
    public val spName: String? = null,
)

/**
 * Decoded Samsung OMC / CSC carrier package details (FR-032, T068).
 */
@Serializable
public data class OmcInfo(
    public val salesCode: String,
    public val country: String? = null,
    public val networks: List<CarrierNetwork> = emptyList(),
    public val features: Map<String, String> = emptyMap(),
)

/**
 * Pure Kotlin parser for Samsung region, carrier, and CSC feature configurations (FR-032).
 * Operates without external tools.
 */
public object OmcDecode {

    public fun decode(salesCodeText: String?, customerXmlText: String?, cscFeatureXmlText: String?): OmcInfo {
        val salesCode = salesCodeText?.trim()?.lines()?.firstOrNull()?.trim()
            ?: extractTag(customerXmlText.orEmpty(), "SalesCode")
            ?: "UNKNOWN"

        val country = customerXmlText?.let { extractTag(it, "Country") }

        val networks = mutableListOf<CarrierNetwork>()
        if (!customerXmlText.isNullOrBlank()) {
            val networkBlocks = extractBlocks(customerXmlText, "NetworkInfo")
            for (block in networkBlocks) {
                val mcc = extractTag(block, "MCC") ?: ""
                val mnc = extractTag(block, "MNC") ?: ""
                val name = extractTag(block, "NetworkName") ?: ""
                val spName = extractTag(block, "SPName")
                if (mcc.isNotEmpty() || mnc.isNotEmpty() || name.isNotEmpty()) {
                    networks.add(
                        CarrierNetwork(
                            mcc = mcc,
                            mnc = mnc,
                            networkName = unescapeXml(name),
                            spName = spName?.let { unescapeXml(it) },
                        ),
                    )
                }
            }
        }

        val features = mutableMapOf<String, String>()
        if (!cscFeatureXmlText.isNullOrBlank()) {
            val featureRegex = "<(CscFeature_[a-zA-Z0-9_]+)>([^<]*)</".toRegex()
            for (match in featureRegex.findAll(cscFeatureXmlText)) {
                val tag = match.groupValues[1]
                val value = match.groupValues[2].trim()
                features[tag] = unescapeXml(value)
            }
        }

        return OmcInfo(
            salesCode = salesCode,
            country = country,
            networks = networks,
            features = features,
        )
    }

    private fun extractTag(xml: String, tag: String): String? {
        val regex = "<$tag>([^<]*)</$tag>".toRegex()
        return regex.find(xml)?.groupValues?.get(1)?.trim()
    }

    private fun extractBlocks(xml: String, tag: String): List<String> {
        val regex = "<$tag[\\s>]([\\s\\S]*?)</$tag>".toRegex()
        return regex.findAll(xml).map { it.groupValues[1] }.toList()
    }

    private fun unescapeXml(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}
