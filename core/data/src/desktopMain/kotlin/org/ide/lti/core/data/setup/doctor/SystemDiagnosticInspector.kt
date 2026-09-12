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
