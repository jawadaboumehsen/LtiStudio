/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin

import org.ide.lti.core.model.plugin.PluginPlanTemplate

/**
 * Access port for installed plugin plan templates and package content digests.
 */
public interface PluginCatalogPort {
    /** Returns the plan template from the installed store, or null if uninstalled or missing. */
    public fun templateFor(publisher: String, id: String, version: String): PluginPlanTemplate?

    /** Returns the verified package content digest, or null if uninstalled or missing. */
    public fun contentDigestFor(publisher: String, id: String, version: String): String?
}

/**
 * Default catalog that returns null for everything; used when no catalog is supplied.
 */
public object EmptyPluginCatalog : PluginCatalogPort {
    override fun templateFor(publisher: String, id: String, version: String): PluginPlanTemplate? = null
    override fun contentDigestFor(publisher: String, id: String, version: String): String? = null
}
