package io.ltirom.tooling.core

public interface ToolOutputParser<out T> {
    public fun parse(stdout: String, stderr: String): T
}

/** Lossless-enough representation for text metadata emitted by image/container tools. */
public data class ParsedToolMetadata(
    val fields: Map<String, String>,
    val lines: List<String>
)

public data class AvbImageInfo(
    val fields: ParsedToolMetadata,
    val algorithm: String? = fields.fields["Algorithm"],
    val hashAlgorithm: String? = fields.fields["Hash Algorithm"],
    val partitionName: String? = fields.fields["Partition Name"],
    val imageSize: Long? = fields.fields["Image Size"]?.toLongOrNull(),
    val rollbackIndex: Long? = fields.fields["Rollback Index"]?.toLongOrNull()
) {
    public fun validate() {
        require(imageSize == null || imageSize >= 0) { "AVB image size cannot be negative" }
        require(rollbackIndex == null || rollbackIndex >= 0) { "AVB rollback index cannot be negative" }
    }
}

public data class DynamicPartitionMetadata(
    val fields: ParsedToolMetadata,
    val metadataSize: Long? = fields.fields["Metadata size"]?.toLongOrNull(),
    val metadataSlots: Int? = fields.fields["Metadata slots"]?.toIntOrNull(),
    val diskSize: Long? = fields.fields["Disk size"]?.toLongOrNull(),
    val partitions: List<String> = fields.lines
        .mapNotNull { Regex("^\\s*Partition(?: name)?:\\s*(\\S+)").matchEntire(it)?.groupValues?.get(1) },
    val groups: List<String> = fields.lines
        .mapNotNull { Regex("^\\s*Group name:\\s*(\\S+)").matchEntire(it)?.groupValues?.get(1) },
    val partitionDescriptors: List<DynamicPartitionDescriptor> = parsePartitionDescriptors(fields.lines)
) {
    public fun validateGeometry() {
        require(metadataSize == null || metadataSize > 0) { "Metadata size must be positive" }
        require(metadataSlots == null || metadataSlots > 0) { "Metadata slots must be positive" }
        require(diskSize == null || diskSize > 0) { "Disk size must be positive" }
        require(partitions.distinct().size == partitions.size) { "Dynamic partition names must be unique" }
        val extents = partitionDescriptors.flatMap { descriptor ->
            descriptor.extents.map { extent -> descriptor.name to extent }
        }.sortedBy { it.second.startBlock }
        var previousEnd = 0L
        for ((name, extent) in extents) {
            require(extent.startBlock >= 0 && extent.endBlock > extent.startBlock) {
                "Invalid extent in dynamic partition $name"
            }
            require(extent.startBlock >= previousEnd) {
                "Overlapping dynamic partition extent at ${extent.startBlock}"
            }
            require(diskSize == null || extent.endBlock <= diskSize / 512L) {
                "Dynamic partition extent exceeds disk geometry: $name"
            }
            previousEnd = maxOf(previousEnd, extent.endBlock)
        }
    }
}

public data class DynamicPartitionDescriptor(
    val name: String,
    val group: String? = null,
    val size: Long? = null,
    val attributes: Set<String> = emptySet(),
    val extents: List<DynamicPartitionExtent> = emptyList()
)

public data class DynamicPartitionExtent(val startBlock: Long, val endBlock: Long, val target: String? = null)

private fun parsePartitionDescriptors(lines: List<String>): List<DynamicPartitionDescriptor> {
    val result = mutableListOf<DynamicPartitionDescriptor>()
    var current: DynamicPartitionDescriptor? = null
    fun flush() { current?.let(result::add) }
    for (line in lines) {
        val name = Regex("^\\s*Partition name:\\s*(\\S+)").matchEntire(line)?.groupValues?.get(1)
        if (name != null) {
            flush()
            current = DynamicPartitionDescriptor(name)
            continue
        }
        val existing = current ?: continue
        val group = Regex("^\\s*Group:\\s*(\\S+)").matchEntire(line)?.groupValues?.get(1)
        val size = Regex("^\\s*Size:\\s*(\\d+)").matchEntire(line)?.groupValues?.get(1)?.toLongOrNull()
        val attrs = Regex("^\\s*Attributes:\\s*(.*)").matchEntire(line)?.groupValues?.get(1)
            ?.split(Regex("\\s+"))?.filter(String::isNotBlank)?.toSet()
        val extent = Regex("^\\s*(\\d+)\\s*\\.\\.\\s*(\\d+)(?:\\s+(\\S+))?").matchEntire(line)?.let {
            DynamicPartitionExtent(it.groupValues[1].toLong(), it.groupValues[2].toLong(), it.groupValues.getOrNull(3)?.ifBlank { null })
        }
        current = existing.copy(
            group = group ?: existing.group,
            size = size ?: existing.size,
            attributes = attrs ?: existing.attributes,
            extents = if (extent != null) existing.extents + extent else existing.extents
        )
    }
    flush()
    return result
}

public data class BootImageMetadata(
    val fields: ParsedToolMetadata,
    val headerVersion: Int? = (
        fields.fields["header version"]
            ?: fields.fields["boot image header version"]
            ?: fields.fields["vendor boot image header version"]
        )?.toIntOrNull(),
    val osVersion: String? = fields.fields["os version"]?.takeIf { it != "None" },
    val ramdiskFormat: String? = fields.fields["ramdisk format"],
    val bootMagic: String? = fields.fields["boot magic"]
) {
    public fun validate() {
        require(headerVersion == null || headerVersion in 0..4) { "Unsupported boot image header version: $headerVersion" }
        require(!bootMagic.isNullOrBlank() || !osVersion.isNullOrBlank() || !ramdiskFormat.isNullOrBlank()) {
            "Boot metadata is empty"
        }
    }
}

/** Parses the stable `name: value`/`name=value` diagnostics used by image tooling. */
public object ToolMetadataParser : ToolOutputParser<ParsedToolMetadata> {
    private val delimited = Regex("^\\s*([^:=]+?)\\s*[:=]\\s*(.*?)\\s*$")

    override fun parse(stdout: String, stderr: String): ParsedToolMetadata {
        val separator = if (stdout.isNotEmpty() && stderr.isNotEmpty()) "\n" else ""
        val lines = (stdout + separator + stderr)
            .lineSequence()
            .toList()
            .let { if (it.lastOrNull().isNullOrEmpty()) it.dropLast(1) else it }
        val fields = linkedMapOf<String, String>()
        for (line in lines) {
            val match = delimited.matchEntire(line) ?: continue
            val key = match.groupValues[1].trim()
            if (key.isNotEmpty()) fields[key] = match.groupValues[2].trim()
        }
        return ParsedToolMetadata(fields.toMap(), lines)
    }
}

public object AvbImageInfoParser : ToolOutputParser<AvbImageInfo> {
    override fun parse(stdout: String, stderr: String): AvbImageInfo = AvbImageInfo(ToolMetadataParser.parse(stdout, stderr)).also { it.validate() }
}

/** Parses avbtool's numeric output from `add_hashtree_footer --calc_max_image_size`. */
public object AvbMaxImageSizeParser : ToolOutputParser<Long?> {
    override fun parse(stdout: String, stderr: String): Long? {
        val text = (stdout + "\n" + stderr).lineSequence()
            .map(String::trim)
            .lastOrNull { it.isNotEmpty() }
            ?: return null
        return text.toLongOrNull()?.takeIf { it >= 0L }
    }
}

public object DynamicPartitionMetadataParser : ToolOutputParser<DynamicPartitionMetadata> {
    override fun parse(stdout: String, stderr: String): DynamicPartitionMetadata = DynamicPartitionMetadata(ToolMetadataParser.parse(stdout, stderr)).also { it.validateGeometry() }
}

public object BootImageMetadataParser : ToolOutputParser<BootImageMetadata> {
    override fun parse(stdout: String, stderr: String): BootImageMetadata = BootImageMetadata(ToolMetadataParser.parse(stdout, stderr)).also { it.validate() }
}

/** Stable representation of fastboot's key/value diagnostics, which are commonly emitted on stderr. */
public object FastbootVariablesParser : ToolOutputParser<Map<String, String>> {
    private val variable = Regex("^\\s*(?:\\(bootloader\\)\\s*)?getvar:([^: ]+):\\s*(.*?)\\s*$")

    override fun parse(stdout: String, stderr: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        (stdout + "\n" + stderr).lineSequence().forEach { line ->
            val match = variable.matchEntire(line) ?: return@forEach
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            if (value.isNotEmpty() && !value.startsWith("FAILED")) {
                result[key] = value
            }
        }
        return result.toMap()
    }
}

public object ToolOutputParserRegistry {
    private val parsers = mutableMapOf<String, ToolOutputParser<*>>()

    init {
        // Register default stub parser for tests or common patterns
        register("identity", object : ToolOutputParser<String> {
            override fun parse(stdout: String, stderr: String): String = stdout
        })
        register("default", object : ToolOutputParser<Unit> {
            override fun parse(stdout: String, stderr: String): Unit {}
        })
        register("adb-devices", object : ToolOutputParser<List<String>> {
            override fun parse(stdout: String, stderr: String): List<String> {
                return stdout.lines()
                    .filter { it.contains("\tdevice") }
                    .map { it.substringBefore("\t") }
            }
        })
        register("fastboot-vars", FastbootVariablesParser)
        register("metadata", ToolMetadataParser)
        register("avb-info", AvbImageInfoParser)
        register("avb-max-image-size", AvbMaxImageSizeParser)
        register("lp-metadata", DynamicPartitionMetadataParser)
        register("boot-image-metadata", BootImageMetadataParser)
        register("adb-transfer-progress", AdbTransferProgressParser)
    }

    public fun register(id: String, parser: ToolOutputParser<*>) {
        synchronized(parsers) {
            require(id !in parsers) { "Duplicate parser ID registered: '$id'" }
            parsers[id] = parser
        }
    }

    public fun get(id: String): ToolOutputParser<*> {
        return parsers[id] ?: throw IllegalArgumentException("No output parser registered with ID '$id'")
    }

    public fun getRegisteredIds(): Set<String> {
        return parsers.keys.toSet()
    }
}
