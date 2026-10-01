/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.patch

import org.ide.lti.core.model.plugin.BytecodeMatchSpec
import java.security.MessageDigest

object BytecodeMatcher {

    sealed interface MatchOutcome {
        data class Matched(val anchorStartLine: Int, val anchorEndLine: Int, val score: Double) : MatchOutcome
        data class Ambiguous(val candidateStartLines: List<Int>) : MatchOutcome
        data class NearMiss(
            val bestStartLine: Int,
            val score: Double,
            val divergingOpcodeIndex: Int,
            val expected: String,
            val actual: String,
        ) : MatchOutcome
        data object NoCandidate : MatchOutcome
        data class MethodNotFound(val methodDescriptor: String) : MatchOutcome
        data class ClassMismatch(val expected: String, val actual: String) : MatchOutcome
        data class PreimageMismatch(val expected: String, val actual: String) : MatchOutcome
    }

    fun match(classSmali: String, spec: BytecodeMatchSpec): MatchOutcome {
        val lines = classSmali.lines()
        val actualClass = extractClassDescriptor(lines) ?: ""
        val methodLines = findMethodLines(lines, spec.methodDescriptor)
        val preimage = methodLines?.let(::preimageOf)
        return when {
            actualClass != spec.classDescriptor -> MatchOutcome.ClassMismatch(spec.classDescriptor, actualClass)
            methodLines == null -> MatchOutcome.MethodNotFound(spec.methodDescriptor)
            preimage != spec.expectedPreimageSha256 ->
                MatchOutcome.PreimageMismatch(spec.expectedPreimageSha256, preimage.orEmpty())
            else -> evaluateAnchor(methodLines, spec)
        }
    }

    fun methodPreimage(classSmali: String, methodDescriptor: String): String? =
        findMethodLines(classSmali.lines(), methodDescriptor)?.let(::preimageOf)

    /** The one preimage definition shared by author tooling and runtime: instruction lines only, trimmed, LF-joined. */
    private fun preimageOf(methodLines: List<IndexedValue<String>>): String =
        sha256(methodLines.map { it.value.trim() }.filter(::isInstruction).joinToString("\n"))

    private fun evaluateAnchor(methodLines: List<IndexedValue<String>>, spec: BytecodeMatchSpec): MatchOutcome {
        val instructions = parseInstructions(methodLines)
        val n = spec.anchor.opcodeSequence.size
        if (n == 0 || instructions.size < n) {
            return MatchOutcome.NoCandidate
        }

        val (fullCandidates, bestCandidate) = findCandidates(instructions, spec)
        return resolveCandidateOutcome(fullCandidates, bestCandidate, spec)
    }

    private fun parseInstructions(methodLines: List<IndexedValue<String>>): List<Instruction> =
        methodLines.filter { isInstruction(it.value.trim()) }.map { indexed ->
            val trimmed = indexed.value.trim()
            val opcode = extractOpcode(trimmed)
            Instruction(
                lineIndex = indexed.index,
                opcode = opcode,
                stringConstant = extractStringConstant(opcode, trimmed),
                invokeSignature = extractInvokeSignature(opcode, trimmed),
            )
        }

    private fun findCandidates(
        instructions: List<Instruction>,
        spec: BytecodeMatchSpec,
    ): Pair<List<WindowCandidate>, WindowCandidate?> {
        val n = spec.anchor.opcodeSequence.size
        val fullCandidates = mutableListOf<WindowCandidate>()
        var bestCandidate: WindowCandidate? = null

        for (w in 0..(instructions.size - n)) {
            val window = instructions.subList(w, w + n)
            val matchingCount = (0 until n).count { i ->
                window[i].opcode == spec.anchor.opcodeSequence[i]
            }
            val opcodeScore = matchingCount.toDouble() / n
            val windowConstants = window.mapNotNull { it.stringConstant }
            val constantsOk = spec.anchor.stringConstants.all { it in windowConstants }
            val invokeOk = spec.anchor.invokeSignature == null ||
                window.any { it.invokeSignature == spec.anchor.invokeSignature }
            val score = opcodeScore * (if (constantsOk) 1.0 else 0.5) * (if (invokeOk) 1.0 else 0.5)

            val candidate = WindowCandidate(
                startLine = window.first().lineIndex,
                endLine = window.last().lineIndex,
                window = window,
                score = score,
                constantsOk = constantsOk,
                invokeOk = invokeOk,
            )

            if (opcodeScore == 1.0 && constantsOk && invokeOk) {
                fullCandidates.add(candidate)
            }

            if (bestCandidate == null || score > bestCandidate.score) {
                bestCandidate = candidate
            }
        }
        return fullCandidates to bestCandidate
    }

    private fun resolveCandidateOutcome(
        fullCandidates: List<WindowCandidate>,
        bestCandidate: WindowCandidate?,
        spec: BytecodeMatchSpec,
    ): MatchOutcome = when {
        fullCandidates.size == 1 -> {
            val matched = fullCandidates.first()
            MatchOutcome.Matched(
                anchorStartLine = matched.startLine,
                anchorEndLine = matched.endLine,
                score = 1.0,
            )
        }
        fullCandidates.size > 1 -> {
            MatchOutcome.Ambiguous(
                candidateStartLines = fullCandidates.map { it.startLine },
            )
        }
        bestCandidate != null -> buildNearMiss(bestCandidate, spec)
        else -> MatchOutcome.NoCandidate
    }

    private fun buildNearMiss(best: WindowCandidate, spec: BytecodeMatchSpec): MatchOutcome.NearMiss {
        val n = spec.anchor.opcodeSequence.size
        val diffIndex = (0 until n).firstOrNull { i ->
            best.window[i].opcode != spec.anchor.opcodeSequence[i]
        }

        return if (diffIndex != null) {
            MatchOutcome.NearMiss(
                bestStartLine = best.startLine,
                score = best.score,
                divergingOpcodeIndex = diffIndex,
                expected = spec.anchor.opcodeSequence[diffIndex],
                actual = best.window[diffIndex].opcode,
            )
        } else {
            val (expected, actual) = resolveFailingDetail(best, spec)
            MatchOutcome.NearMiss(
                bestStartLine = best.startLine,
                score = best.score,
                divergingOpcodeIndex = -1,
                expected = expected,
                actual = actual,
            )
        }
    }

    private fun resolveFailingDetail(best: WindowCandidate, spec: BytecodeMatchSpec): Pair<String, String> {
        if (!best.constantsOk) {
            val windowConstants = best.window.mapNotNull { it.stringConstant }
            val failingConstant = spec.anchor.stringConstants.first { it !in windowConstants }
            val actualConstant = best.window.mapNotNull { it.stringConstant }.firstOrNull() ?: ""
            return failingConstant to actualConstant
        }
        val failingSig = spec.anchor.invokeSignature ?: ""
        val actualSig = best.window.mapNotNull { it.invokeSignature }.firstOrNull() ?: ""
        return failingSig to actualSig
    }

    private fun sha256(content: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun extractClassDescriptor(lines: List<String>): String? {
        val classLine = lines.firstOrNull {
            val trimmed = it.trim()
            trimmed.startsWith(".class ") || trimmed.startsWith(".class\t")
        } ?: return null

        return classLine.trim().split(Regex("\\s+")).lastOrNull { it.startsWith("L") && it.endsWith(";") }
    }

    private fun isMethodHeader(trimmed: String, methodDescriptor: String): Boolean {
        val prefix = trimmed.removeSuffix(methodDescriptor)
        return trimmed.startsWith(".method") &&
            trimmed.endsWith(methodDescriptor) &&
            prefix.isNotEmpty() &&
            prefix.last().isWhitespace()
    }

    private fun findMethodLines(lines: List<String>, methodDescriptor: String): List<IndexedValue<String>>? {
        val headerIndex = lines.indexOfFirst { isMethodHeader(it.trim(), methodDescriptor) }
        if (headerIndex == -1) return null

        val methodLines = mutableListOf<IndexedValue<String>>()
        for (i in (headerIndex + 1) until lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed == ".end method" ||
                trimmed.startsWith(".end method ") ||
                trimmed.startsWith(".end method\t")
            ) {
                break
            }
            methodLines.add(IndexedValue(i, lines[i]))
        }
        return methodLines
    }

    private fun isInstruction(trimmed: String): Boolean =
        trimmed.isNotEmpty() && trimmed[0].let { it != '.' && it != ':' && it != '#' }

    private fun extractOpcode(trimmed: String): String {
        val spaceIndex = trimmed.indexOfFirst { it.isWhitespace() }
        return if (spaceIndex == -1) trimmed else trimmed.substring(0, spaceIndex)
    }

    private fun extractStringConstant(opcode: String, trimmed: String): String? {
        if (opcode != "const-string" && opcode != "const-string/jumbo") return null
        val firstQuote = trimmed.indexOf('"')
        val lastQuote = trimmed.lastIndexOf('"')
        return if (firstQuote != -1 && lastQuote > firstQuote) {
            trimmed.substring(firstQuote + 1, lastQuote)
        } else {
            null
        }
    }

    private fun extractInvokeSignature(opcode: String, trimmed: String): String? {
        if (!opcode.startsWith("invoke-") || ',' !in trimmed) return null
        return trimmed.substringAfterLast(',').trim()
    }

    private data class Instruction(
        val lineIndex: Int,
        val opcode: String,
        val stringConstant: String?,
        val invokeSignature: String?,
    )

    private data class WindowCandidate(
        val startLine: Int,
        val endLine: Int,
        val window: List<Instruction>,
        val score: Double,
        val constantsOk: Boolean,
        val invokeOk: Boolean,
    )
}
