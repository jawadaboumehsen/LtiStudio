package io.ltirom.tooling.core

data class AdbSerial(val value: String) {
    init {
        require(value.isNotBlank()) { "ADB serial cannot be blank" }
    }
}

data class FastbootSerial(val value: String) {
    init {
        require(value.isNotBlank()) { "Fastboot serial cannot be blank" }
    }
}

enum class DeviceState(val transportName: String) {
    DEVICE("device"),
    RECOVERY("recovery"),
    SIDELOAD("sideload"),
    FASTBOOT("fastboot"),
    FASTBOOTD("fastbootd");

    companion object {
        fun fromTransportNameOrNull(name: String): DeviceState? {
            return entries.firstOrNull { it.transportName.equals(name, ignoreCase = true) }
        }
    }
}

enum class Slot {
    A, B, NONE;

    fun suffix(): String = when (this) {
        A -> "_a"
        B -> "_b"
        NONE -> ""
    }

    companion object {
        fun fromSuffixOrNull(suffix: String): Slot? {
            return when (suffix.lowercase()) {
                "_a", "a" -> A
                "_b", "b" -> B
                "" -> NONE
                else -> null
            }
        }
    }
}

enum class PartitionType {
    LOGICAL,
    PHYSICAL
}

data class PartitionName(val rawName: String) {
    val baseName: String
    val slot: Slot

    init {
        require(rawName.isNotBlank()) { "Partition name cannot be blank" }
        val lower = rawName.lowercase()
        when {
            lower.endsWith("_a") -> {
                baseName = rawName.substring(0, rawName.length - 2)
                slot = Slot.A
            }
            lower.endsWith("_b") -> {
                baseName = rawName.substring(0, rawName.length - 2)
                slot = Slot.B
            }
            else -> {
                baseName = rawName
                slot = Slot.NONE
            }
        }
    }

    fun withSlot(newSlot: Slot): PartitionName {
        return PartitionName(baseName + newSlot.suffix())
    }
}

data class PartitionConstraint(
    val name: PartitionName,
    val type: PartitionType,
    val allowlistedForFlash: Boolean
) {
    companion object {
        private val ALLOWLISTED_PARTITIONS = setOf(
            "boot", "init_boot", "vendor_boot", "dtbo", "vbmeta", "vbmeta_system", "vbmeta_vendor",
            "system", "system_ext", "vendor", "product", "odm", "recovery", "super"
        )

        fun getFor(name: PartitionName): PartitionConstraint {
            val isLogical = name.baseName in setOf("system", "system_ext", "vendor", "product", "odm")
            val isAllowlisted = name.baseName in ALLOWLISTED_PARTITIONS

            return PartitionConstraint(
                name = name,
                type = if (isLogical) PartitionType.LOGICAL else PartitionType.PHYSICAL,
                allowlistedForFlash = isAllowlisted
            )
        }
    }
}
