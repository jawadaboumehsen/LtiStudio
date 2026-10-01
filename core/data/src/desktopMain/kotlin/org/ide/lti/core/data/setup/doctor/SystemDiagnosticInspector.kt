/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.doctor

import org.ide.lti.core.domain.setup.DiagnosticCheckItem

/**
 * Strategy interface for single-responsibility system diagnostic inspectors.
 *
 * Adheres to:
 * - Interface Segregation Principle (ISP): Each inspector performs its narrow inspection.
 * - Open/Closed Principle (OCP): New diagnostic checks are added by implementing new inspectors
 *   without modifying existing inspectors or the orchestrator engine.
 */
public interface SystemDiagnosticInspector {
    /**
     * Executes the diagnostic check against the target environment.
     */
    public suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem
}
