/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin.edit

import org.ide.lti.core.domain.plugin.ComponentPatchCodes
import org.ide.lti.core.model.plugin.XmlChange
import org.ide.lti.core.model.plugin.XmlChangeKind
import org.ide.lti.core.model.plugin.XmlMatch
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Applies [XmlChange]s to a decoded resource as a document, so an edit does not depend on the file's formatting,
 * line order or anything else in it: elements are picked by tag and attributes. A resource element added to the
 * root (`<string name=…>`, `<public type=… name=… id=…>`) is refused when one with the same name, or a public id, is
 * already there, which aapt would otherwise reject much later.
 */
@Suppress("ReturnCount")
object XmlResourceEditor {

    private class Refused(val code: String, message: String) : Exception(message)

    fun apply(xml: String, changes: List<XmlChange>, member: String): EditResult = try {
        val doc = parse(xml)
        changes.forEach { apply(doc, it) }
        EditResult.Changed(write(doc), member)
    } catch (e: Refused) {
        EditResult.Failed(e.code, e.message.orEmpty())
    } catch (e: org.xml.sax.SAXException) {
        EditResult.Failed(ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND, "Not well-formed XML: ${e.message}")
    }

    private fun apply(doc: Document, change: XmlChange) {
        val root = doc.documentElement
        val nodes = fragment(doc, root, change.fragment)
        if (change.kind == XmlChangeKind.APPEND && change.match == null) {
            nodes.filterIsInstance<Element>().forEach { checkNotDeclared(root, it) }
            nodes.forEach { appendIndented(root, it) }
            return
        }
        val match = change.match ?: throw Refused(ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND, "No element named")
        val target = single(root, match)
        val parent = target.parentNode
        when (change.kind) {
            XmlChangeKind.APPEND -> nodes.forEach { appendIndented(target, it) }
            XmlChangeKind.INSERT_BEFORE -> nodes.forEach { insertIndented(parent, it, target, like = target) }
            XmlChangeKind.INSERT_AFTER ->
                nodes.asReversed().forEach { insertIndented(parent, it, target.nextSibling, like = target) }
            XmlChangeKind.REPLACE -> {
                nodes.forEach { parent.insertBefore(it, target) }
                parent.removeChild(target)
            }
            XmlChangeKind.REMOVE -> parent.removeChild(target)
        }
    }

    private fun single(root: Element, match: XmlMatch): Element {
        val all = root.getElementsByTagName("*")
        val hits = (listOf(root) + (0 until all.length).map { all.item(it) as Element }).filter { matches(it, match) }
        return when (hits.size) {
            1 -> hits.single()
            0 -> throw Refused(
                ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND,
                "No element ${describe(match)}" + near(root, match),
            )
            else -> throw Refused(
                ComponentPatchCodes.PATCH_CONTEXT_AMBIGUOUS,
                "${hits.size} elements ${describe(match)}",
            )
        }
    }

    private fun matches(e: Element, match: XmlMatch): Boolean =
        (match.tag == null || match.tag == e.nodeName || match.tag == e.localName) &&
            match.attributes.all { (name, value) -> e.hasAttribute(name) && e.getAttribute(name) == value }

    /**
     * The elements of the tag [match] asks for (or, for any tag, those that have its first attribute), shown with
     * the attributes it names, so a renamed key or a changed value is easy to spot.
     */
    private fun near(root: Element, match: XmlMatch): String {
        val all = root.getElementsByTagName("*").let { list -> (0 until list.length).map { list.item(it) as Element } }
        val firstAttribute = match.attributes.keys.firstOrNull()
        val similar = all.filter { e ->
            if (match.tag != null) {
                match.tag == e.nodeName || match.tag == e.localName
            } else {
                firstAttribute?.let(e::hasAttribute) == true
            }
        }
        if (similar.isEmpty()) return "; the document has no such element"
        val shown = similar.take(MAX_LISTED).joinToString("") { e ->
            "\n  <${e.nodeName}" + match.attributes.keys.filter(e::hasAttribute)
                .joinToString("") { " $it=\"${e.getAttribute(it)}\"" } + ">"
        }
        val more = (similar.size - MAX_LISTED).takeIf { it > 0 }?.let { "\n  … and $it more" }.orEmpty()
        return "; similar elements:$shown$more"
    }

    private const val MAX_LISTED = 8

    private fun describe(match: XmlMatch): String =
        "<${match.tag ?: "*"}" + match.attributes.entries.joinToString("") { " ${it.key}=\"${it.value}\"" } + ">"

    /** A resource added to the root must not exist already: same tag, type and name, or the same public id. */
    private fun checkNotDeclared(root: Element, added: Element) {
        val name = added.getAttribute("name").takeIf { it.isNotEmpty() } ?: return
        val siblings = (0 until root.childNodes.length).map { root.childNodes.item(it) }.filterIsInstance<Element>()
        val clash = siblings.firstOrNull { e ->
            e.nodeName == added.nodeName && e.getAttribute("type") == added.getAttribute("type") &&
                e.getAttribute("name") == name
        } ?: siblings.firstOrNull { e ->
            added.nodeName == "public" && e.nodeName == "public" && added.getAttribute("id").isNotEmpty() &&
                e.getAttribute("id").equals(added.getAttribute("id"), ignoreCase = true)
        }
        if (clash != null) {
            throw Refused(
                ComponentPatchCodes.PATCH_CONTEXT_AMBIGUOUS,
                "<${added.nodeName} name=\"$name\"> clashes with " +
                    "<${clash.nodeName} name=\"${clash.getAttribute("name")}\"" +
                    clash.getAttribute("id").takeIf { it.isNotEmpty() }?.let { " id=\"$it\"" }.orEmpty() + ">",
            )
        }
    }

    /** [fragment]'s nodes, parsed with the root's namespace prefixes (`android:`, `app:`) in scope. */
    private fun fragment(doc: Document, root: Element, fragment: String): List<Node> {
        if (fragment.isBlank()) return emptyList()
        val namespaces = (0 until root.attributes.length).map { root.attributes.item(it) }
            .filter { it.nodeName.startsWith("xmlns") }
            .joinToString("") { " ${it.nodeName}=\"${it.nodeValue}\"" }
        val wrapper = parse("<lti-fragment$namespaces>$fragment</lti-fragment>").documentElement
        return (0 until wrapper.childNodes.length).map { wrapper.childNodes.item(it) }
            .filter { it.nodeType != Node.TEXT_NODE || it.nodeValue.isNotBlank() }
            .map { doc.importNode(it, true) }
    }

    private fun appendIndented(parent: Node, node: Node) {
        val lastElement = (0 until parent.childNodes.length).map { parent.childNodes.item(it) }
            .lastOrNull { it is Element }
        insertIndented(parent, node, lastElement?.nextSibling, like = lastElement)
    }

    /**
     * Inserts [node] before [before], indented like [like] (the element it goes next to): a line break and that
     * indentation go between [like] and [node].
     */
    private fun insertIndented(parent: Node, node: Node, before: Node?, like: Node?) {
        val indent = like?.previousSibling
            ?.takeIf { it.nodeType == Node.TEXT_NODE && it.nodeValue.isBlank() }
            ?.nodeValue?.substringAfterLast('\n')
            ?.let { parent.ownerDocument.createTextNode("\n$it") }
        if (like === before) {
            parent.insertBefore(node, before)
            indent?.let { parent.insertBefore(it, before) }
        } else {
            indent?.let { parent.insertBefore(it, before) }
            parent.insertBefore(node, before)
        }
    }

    private fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Plugin content is not trusted: no DOCTYPE, so no external entities.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }
        return factory.newDocumentBuilder().apply { setErrorHandler(null) }.parse(InputSource(StringReader(xml)))
    }

    private fun write(doc: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "utf-8")
        }
        doc.xmlStandalone = true
        // The transformer writes the platform's line breaks (CRLF on Windows); decoded resources use \n.
        return StringWriter().also { transformer.transform(DOMSource(doc), StreamResult(it)) }.toString()
            .replace("\r\n", "\n").replaceFirst("?><", "?>\n<") + "\n"
    }
}
