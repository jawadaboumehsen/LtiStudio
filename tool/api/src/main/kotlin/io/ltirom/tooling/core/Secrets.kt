package io.ltirom.tooling.core

class SecretString(private val rawValue: String) {
    fun getUnsafeValue(): String = rawValue

    override fun toString(): String = "[REDACTED]"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecretString) return false
        return rawValue == other.rawValue
    }

    override fun hashCode(): Int {
        return rawValue.hashCode()
    }
}

class Redactor {
    private val patterns = mutableSetOf<String>()

    fun register(secret: String) {
        if (secret.length >= 4) {
            patterns.add(secret)
        }
    }

    fun register(secret: SecretString) {
        register(secret.getUnsafeValue())
    }

    fun redact(input: String): String {
        if (patterns.isEmpty()) return input
        var current = input
        for (pattern in patterns) {
            current = current.replace(pattern, "[REDACTED]")
        }
        return current
    }
}
