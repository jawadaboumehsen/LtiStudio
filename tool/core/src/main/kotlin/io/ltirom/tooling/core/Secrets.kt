package io.ltirom.tooling.core

public class SecretString(private val rawValue: String) {
    public fun getUnsafeValue(): String = rawValue

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

public class Redactor {
    private val patterns = mutableSetOf<String>()

    public fun register(secret: String) {
        if (secret.length >= 4) {
            patterns.add(secret)
        }
    }

    public fun register(secret: SecretString) {
        register(secret.getUnsafeValue())
    }

    public fun redact(input: String): String {
        if (patterns.isEmpty()) return input
        var current = input
        for (pattern in patterns) {
            current = current.replace(pattern, "[REDACTED]")
        }
        return current
    }
}
