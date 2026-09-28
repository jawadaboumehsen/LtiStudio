package org.ide.lti.core.model.setup

public data class SetupEnvironment(
    val distro: String,
    val wslVersion: Int,
    val osId: String,
    val osVersionId: String,
    val user: String,
    val home: String,
)
