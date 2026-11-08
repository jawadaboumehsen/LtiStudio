/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.debloat

/**
 * An app icon that is XML (an adaptive icon or a vector drawable), from the `.vec` bundle [AppManifests.LABELS_SCRIPT]
 * writes: `== root` then the icon's `aapt2 dump xmltree`, and one `== <id> xml|img <file>|color <#argb>` section per
 * resource it refers to. Each layer becomes a colour, a bitmap file or an SVG document, for the UI to draw in order.
 */
public object VectorIcons {
    /** One layer, drawn inside its box shrunk by [inset] (a fraction of the box) on every side. */
    public sealed interface Layer {
        public val inset: Float

        public data class Fill(val argb: Long, override val inset: Float = 0f) : Layer

        /** A bitmap file in the bundle's folder. */
        public data class Bitmap(val file: String, override val inset: Float = 0f) : Layer

        /** An SVG document whose viewBox is the vector's viewport. */
        public data class Svg(val svg: String, override val inset: Float = 0f) : Layer
    }

    /** [layers] bottom to top; an [adaptive] icon is 108 units of which a launcher shows the middle 72. */
    public data class Icon(val layers: List<Layer>, val adaptive: Boolean)

    public fun parse(bundle: String): Icon? {
        val sections = sections(bundle)
        val root = sections["root"]?.let { tree(it.body) } ?: return null
        val refs = Refs(sections)
        return when (root.name) {
            "adaptive-icon" -> Icon(
                listOf("background", "foreground").flatMap { side ->
                    root.kids.firstOrNull { it.name == side }?.let { refs.content(it, 0f) }.orEmpty()
                },
                adaptive = true,
            )

            else -> refs.element(root, 0f).takeIf { it.isNotEmpty() }?.let { Icon(it, adaptive = false) }
        }
    }

    private class Section(val kind: String, val arg: String?, val body: String)

    private fun sections(bundle: String): Map<String, Section> {
        val out = mutableMapOf<String, Section>()
        var header: List<String>? = null
        val body = StringBuilder()
        fun flush() {
            header?.let { out[it[0]] = Section(it.getOrElse(1) { "xml" }, it.getOrNull(2), body.toString()) }
            body.clear()
        }
        bundle.lineSequence().forEach { line ->
            if (line.startsWith("== ")) {
                flush()
                header = line.removePrefix("== ").trim().split(' ')
            } else {
                body.appendLine(line)
            }
        }
        flush()
        return out
    }

    private class El(val name: String) {
        val attrs = mutableMapOf<String, String>()
        val kids = mutableListOf<El>()
    }

    /** `aapt2 dump xmltree` text as elements: nesting comes from the indentation. */
    private fun tree(text: String): El? {
        val top = El("#")
        val stack = ArrayDeque(listOf(-1 to top))
        text.lineSequence().forEach { line ->
            val indent = line.indexOfFirst { it != ' ' }
            if (indent < 0) return@forEach
            val t = line.substring(indent)
            if (t.startsWith("E: ")) {
                val el = El(t.removePrefix("E: ").substringBefore(" "))
                while (stack.last().first >= indent) stack.removeLast()
                stack.last().second.kids += el
                stack.addLast(indent to el)
            } else if (t.startsWith("A: ") && ")=" in t) {
                val key = t.substringBefore("(0x").substringAfterLast(':')
                val raw = t.substringAfter(")=")
                val quoted = raw.startsWith('"')
                stack.last().second.attrs[key] =
                    if (quoted) raw.substring(1).substringBeforeLast("\" (Raw:").removeSuffix("\"") else raw
            }
        }
        return top.kids.firstOrNull()
    }

    private class Refs(private val sections: Map<String, Section>) {
        private var depth = 0

        /** A `<background>`, `<foreground>` or `<item>`: its `drawable` reference, else its child element. */
        fun content(holder: El, inset: Float): List<Layer> = holder.attrs["drawable"]?.let { ref(it, inset) }
            ?: holder.kids.firstOrNull()?.let { element(it, inset) }
            ?: emptyList()

        fun ref(value: String, inset: Float): List<Layer> {
            val fill = color(value)
            val section = sections[value.removePrefix("@")]
            return when {
                fill != null -> listOf(Layer.Fill(fill, inset))
                section?.kind == "img" -> section.arg?.let { listOf(Layer.Bitmap(it, inset)) }.orEmpty()
                section?.kind == "xml" -> tree(section.body)?.let { element(it, inset) }.orEmpty()
                else -> emptyList()
            }
        }

        fun element(el: El, inset: Float): List<Layer> {
            if (++depth > MAX_DEPTH) return emptyList()
            try {
                return when (el.name) {
                    "vector" -> listOf(Layer.Svg(svg(el), inset))
                    "inset" -> content(el, inset + fraction(el.attrs["inset"]) * (1 - 2 * inset))
                    "layer-list" -> el.kids.filter { it.name == "item" }.flatMap { content(it, inset) }
                    "bitmap" -> el.attrs["src"]?.let { ref(it, inset) }.orEmpty()
                    "color" -> el.attrs["color"]?.let { ref(it, inset) }.orEmpty()
                    else -> emptyList()
                }
            } finally {
                depth--
            }
        }

        /** A literal `#argb`, a colour resource of the bundle, or one of the framework colours icons use. */
        fun color(value: String): Long? = when {
            value.startsWith("#") -> argb(value)

            else -> sections[value.removePrefix("@")]?.takeIf { it.kind == "color" }?.arg?.let(::argb)
                ?: FRAMEWORK_COLORS[value.removePrefix("@")]
        }

        private fun svg(vector: El): String {
            val w = vector.attrs["viewportWidth"]?.toFloatOrNull() ?: DEFAULT_VIEWPORT
            val h = vector.attrs["viewportHeight"]?.toFloatOrNull() ?: DEFAULT_VIEWPORT
            val defs = StringBuilder()
            val body = vector.kids.joinToString("") { node(it, defs) }
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w $h\"><defs>$defs</defs>$body</svg>"
        }

        private fun node(el: El, defs: StringBuilder): String = when (el.name) {
            "group" -> "<g transform=\"${transform(el.attrs)}\">${el.kids.joinToString("") { node(it, defs) }}</g>"
            "path" -> path(el, defs)
            else -> ""
        }

        private fun path(el: El, defs: StringBuilder): String {
            val a = el.attrs
            val d = a["pathData"] ?: return ""
            val fill = paint(a["fillColor"], a["fillAlpha"], defs) ?: ("none" to 1f)
            val out = StringBuilder("<path d=\"${escape(d)}\" fill=\"${fill.first}\" fill-opacity=\"${fill.second}\"")
            if (a["fillType"] == "1") out.append(" fill-rule=\"evenodd\"")
            paint(a["strokeColor"], a["strokeAlpha"], defs)?.let { (color, opacity) ->
                out.append(" stroke=\"$color\" stroke-opacity=\"$opacity\" stroke-width=\"${a["strokeWidth"] ?: "0"}\"")
            }
            return out.append("/>").toString()
        }

        /** An SVG paint and its opacity: a colour, or a gradient written into [defs]. */
        private fun paint(value: String?, alpha: String?, defs: StringBuilder): Pair<String, Float>? {
            val a = alpha?.toFloatOrNull() ?: 1f
            val c = value?.let(::color)
            return when {
                value == null -> null
                c != null -> hex(c) to a * opacity(c)
                else -> gradient(value, defs)?.let { "url(#$it)" to a }
            }
        }

        /** The gradient resource [value] names, written into [defs]; its id there, or null when it is not one. */
        private fun gradient(value: String, defs: StringBuilder): String? {
            val gradient = sections[value.removePrefix("@")]?.takeIf { it.kind == "xml" }?.let { tree(it.body) }
                ?.takeIf { it.name == "gradient" } ?: return null
            val id = "g${defs.length}"
            val stops = gradient.kids.filter { it.name == "item" }.joinToString("") { stop ->
                val c = stop.attrs["color"]?.let(::color) ?: 0L
                val offset = stop.attrs["offset"]?.toFloatOrNull() ?: 0f
                "<stop offset=\"$offset\" stop-color=\"${hex(c)}\" stop-opacity=\"${opacity(c)}\"/>"
            }
            val g = gradient.attrs
            val (tag, coords) = if (g["type"] == "1") {
                "radialGradient" to "cx=\"${g["centerX"]}\" cy=\"${g["centerY"]}\" r=\"${g["gradientRadius"]}\""
            } else {
                "linearGradient" to
                    "x1=\"${g["startX"]}\" y1=\"${g["startY"]}\" x2=\"${g["endX"]}\" y2=\"${g["endY"]}\""
            }
            defs.append("<$tag id=\"$id\" gradientUnits=\"userSpaceOnUse\" $coords>$stops</$tag>")
            return id
        }
    }

    private fun hex(argb: Long) = "#" + (argb and RGB_MASK).toString(HEX).padStart(RGB_DIGITS, '0')

    private fun opacity(argb: Long) = (argb shr ALPHA_SHIFT) / ALPHA_MAX

    /** Android applies a group's scale and rotation about its pivot, then its translation. */
    private fun transform(a: Map<String, String>): String {
        fun f(key: String, default: Float) = a[key]?.toFloatOrNull() ?: default
        val px = f("pivotX", 0f)
        val py = f("pivotY", 0f)
        return "translate(${f("translateX", 0f) + px} ${f("translateY", 0f) + py}) rotate(${f("rotation", 0f)}) " +
            "scale(${f("scaleX", 1f)} ${f("scaleY", 1f)}) translate(${-px} ${-py})"
    }

    /** aapt2 prints a fraction such as `30%` as `0.300000%`: a value under 1 is already the fraction. */
    private fun fraction(value: String?): Float {
        val n = value?.removeSuffix("%")?.toFloatOrNull() ?: return 0f
        return (if (n > 1f) n / PERCENT else n).coerceIn(0f, MAX_INSET)
    }

    private fun argb(hex: String): Long? {
        val digits = hex.removePrefix("#")
        val v = digits.toLongOrNull(HEX) ?: return null
        return when (digits.length) {
            RGB_DIGITS -> v or OPAQUE
            ARGB_DIGITS -> v
            else -> null
        }
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")

    private const val HEX = 16
    private const val RGB_DIGITS = 6
    private const val ARGB_DIGITS = 8
    private const val RGB_MASK = 0xFFFFFFL
    private const val OPAQUE = 0xFF000000L
    private const val ALPHA_SHIFT = 24
    private const val ALPHA_MAX = 255f
    private const val PERCENT = 100f
    private const val MAX_INSET = 0.45f
    private const val DEFAULT_VIEWPORT = 24f
    private const val MAX_DEPTH = 8

    /** `@android:color/white`, `black` and `transparent`: the framework colours icons refer to. */
    private val FRAMEWORK_COLORS = mapOf("0x0106000b" to 0xFFFFFFFFL, "0x0106000c" to 0xFF000000L, "0x0106000d" to 0L)
}
