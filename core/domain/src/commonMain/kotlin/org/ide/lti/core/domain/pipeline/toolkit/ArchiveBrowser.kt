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

/** One member of an archive: its size unpacked and packed, and how it is packed (`Deflate`, `Store`, `LZMA2:23`). */
public data class ArchiveMember(
    val path: String,
    val size: Long?,
    val packedSize: Long?,
    val method: String?,
    val folder: Boolean = false,
)

/**
 * What one level of an archive holds. [format] is read from the file's first bytes; a file
 * that is not an archive (an image, a payload) has no [members], and [notArchive] says so.
 */
public data class ArchiveListing(
    val format: PackageFormat,
    val members: List<ArchiveMember> = emptyList(),
    val notArchive: Boolean = false,
)

/**
 * Browses an archive and the archives inside it with 7-Zip (zip, 7z, rar, tar, gz, xz, zstd, bz2) and lz4, which
 * 7-Zip does not read. A chain of members leads inside: each is unpacked into a cache folder in turn, after its
 * first 512 bytes show it is an archive, so opening a 7 GB payload never unpacks it.
 */
public object ArchiveBrowser {
    /** Single-stream compressors: one member, unpacked without naming it. */
    private val STREAMS = setOf(
        PackageFormat.Gz,
        PackageFormat.Xz,
        PackageFormat.Zstd,
        PackageFormat.Bz2,
        PackageFormat.Lz4,
        PackageFormat.Brotli,
    )

    /**
     * `sh -c SCRIPT sh <list|extract> <7zz> <cache dir> <dest dir> <archive> [chain member…] [-- member…]`: prints
     * `magic <hex>` of the innermost file, then (list) 7-Zip's `-slt` listing or `lz4`, or (extract) `extracted
     * <member>` per member copied into the dest dir. `notarchive <member>` stops at a chain member that is no archive.
     */
    public const val SCRIPT: String = """
        set -e
        mode="$1"; z="$2"; c="$3"; dest="$4"; f="$5"; shift 5
        mkdir -p "${'$'}c"
        magic() { xxd -p -l 8 "$1" | tr -d '\n'; }
        stream() { case "$1" in 1f8b*|fd377a585a00*|28b52ffd*|425a68*) return 0 ;; esac; return 1; }
        lz4ish() { case "$1" in 04224d18*|02214c18*) return 0 ;; esac; return 1; }
        # Writes member $2 of file $1 (any member of a single stream) to stdout. sh has no locals: hence "_mg".
        cat_member() {
          _mg=$(magic "$1")
          if lz4ish "${'$'}_mg"; then lz4 -dc "$1"
          elif stream "${'$'}_mg"; then "${'$'}z" e -so "$1"
          else "${'$'}z" e -so -spd "$1" -- "$2"
          fi
        }
        container() {
          case "$1" in 504b0304*|504b0506*|377abcaf271c*|526172211a07*|1f8b*|fd377a585a00*|28b52ffd*|425a68*|04224d18*|02214c18*) return 0 ;; esac
          [ "$(printf %s "$1" | cut -c515-524)" = 7573746172 ]
        }
        while [ ${'$'}# -gt 0 ] && [ "$1" != "--" ]; do
          # Keyed by the parent's path, size and time: a replaced archive never serves its old members.
          k="${'$'}c/$(printf '%s\n' "${'$'}f" "$(stat -c %s.%.9Y.%i "${'$'}f")" "$1" | sha1sum | cut -c1-16)"
          if [ ! -s "${'$'}k" ]; then
            peek=$(cat_member "${'$'}f" "$1" 2>/dev/null | head -c 512 | xxd -p | tr -d '\n')
            if ! container "${'$'}peek"; then echo "magic ${'$'}peek"; echo "notarchive $1"; exit 0; fi
            cat_member "${'$'}f" "$1" > "${'$'}k.partial" && mv "${'$'}k.partial" "${'$'}k"
          fi
          f="${'$'}k"; shift
        done
        [ "${'$'}{1:-}" = "--" ] && shift
        echo "magic $(xxd -p -l 512 "${'$'}f" | tr -d '\n')"
        if [ "${'$'}mode" = list ]; then
          if lz4ish "$(magic "${'$'}f")"; then echo lz4; else "${'$'}z" l -slt -ba "${'$'}f" || [ ${'$'}? -eq 1 ]; fi
        else
          for m in "$@"; do
            case "/${'$'}m/" in */../*|//*) echo "unsafe member path: ${'$'}m" >&2; exit 2 ;; esac
            mkdir -p "${'$'}dest/$(dirname "${'$'}m")"
            # Resolved, the folder must still be inside dest: a link already there must not lead the write out.
            case "$(realpath "${'$'}dest/$(dirname "${'$'}m")")/" in "$(realpath "${'$'}dest")"/*) ;;
              *) echo "unsafe member path: ${'$'}m" >&2; exit 2 ;; esac
            rm -f "${'$'}dest/${'$'}m"
            cat_member "${'$'}f" "${'$'}m" > "${'$'}dest/${'$'}m"
            echo "extracted ${'$'}m"
          done
        fi
    """

    /** [SCRIPT]'s list output for the file named [name] (its last chain member, or the archive itself). */
    public fun parse(output: String, name: String): ArchiveListing {
        val lines = output.lines()
        val format = lines.firstOrNull { it.startsWith("magic ") }?.removePrefix("magic ")
            ?.let { FormatDetector.detect(FormatDetector.fromHex(it)) } ?: PackageFormat.Unknown
        // A single stream's one member is the file without its compression suffix (7-Zip may not name it).
        val inner = name.substringAfterLast('/').substringBeforeLast('.').ifEmpty { name }
        return when {
            lines.any { it.startsWith("notarchive ") } -> ArchiveListing(format, notArchive = true)
            "lz4" in lines -> ArchiveListing(format, listOf(ArchiveMember(inner, null, null, "LZ4")))
            else -> members(lines, format, inner)
        }
    }

    private fun members(lines: List<String>, format: PackageFormat, inner: String): ArchiveListing {
        val members = blocks(lines).mapNotNull { fields ->
            val path = fields["Path"]?.takeIf { it.isNotEmpty() } ?: inner.takeIf { format in STREAMS }
                ?: return@mapNotNull null
            ArchiveMember(
                path = path,
                size = fields["Size"]?.toLongOrNull(),
                packedSize = fields["Packed Size"]?.toLongOrNull(),
                method = fields["Method"]?.takeIf { it.isNotEmpty() },
                folder = fields["Folder"] == "+",
            )
        }
        return ArchiveListing(format, members, notArchive = members.isEmpty() && format !in ARCHIVES)
    }

    /** Formats 7-Zip lists members of. */
    private val ARCHIVES =
        STREAMS + setOf(PackageFormat.Zip, PackageFormat.SevenZ, PackageFormat.Rar, PackageFormat.Tar)

    /** `-slt` output as one map per member (`Key = value` lines, a blank line between members). */
    private fun blocks(lines: List<String>): List<Map<String, String>> {
        val all = mutableListOf<Map<String, String>>()
        var current = mutableMapOf<String, String>()
        lines.filterNot { it.startsWith("magic ") }.forEach { line ->
            val eq = line.indexOf(" = ")
            when {
                line.isBlank() -> if (current.isNotEmpty()) {
                    all += current
                    current = mutableMapOf()
                }

                eq > 0 -> current[line.substring(0, eq)] = line.substring(eq + 3)

                // "Path = " with nothing after it ends without the space.
                line.endsWith(" =") -> current[line.removeSuffix(" =")] = ""
            }
        }
        if (current.isNotEmpty()) all += current
        return all
    }
}
