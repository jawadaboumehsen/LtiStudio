package org.ide.lti.core.data.setup

import org.ide.lti.core.model.setup.Need
import org.ide.lti.core.model.setup.RequirementCheck
import org.ide.lti.core.model.setup.SystemRequirement

/**
 * Authoritative system requirements catalog derived directly from inspectors and build recipes.
 * Replaces legacy PackageCatalog.
 */
public object RequirementCatalog {

    public val REQUIREMENTS: List<SystemRequirement> = listOf(
        // SERVICE: Needed for build service runtime
        SystemRequirement(
            id = "java-server",
            check = RequirementCheck.JavaMajor(21),
            aptPackages = listOf("openjdk-21-jdk"),
            neededBy = Need.SERVICE,
        ),

        // BUILD: Java compiler for tools and compilation
        SystemRequirement(
            id = "jdk-17",
            check = RequirementCheck.Package("openjdk-17-jdk"),
            aptPackages = listOf("openjdk-17-jdk"),
            neededBy = Need.BUILD,
        ),

        // BUILD: Host Compilers & Build Tools
        SystemRequirement(
            id = "build-essential",
            check = RequirementCheck.Package("build-essential"),
            aptPackages = listOf("build-essential"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "cmake",
            check = RequirementCheck.Command("cmake"),
            aptPackages = listOf("cmake"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "make",
            check = RequirementCheck.Command("make"),
            aptPackages = listOf("make"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "clang",
            check = RequirementCheck.Command("clang"),
            aptPackages = listOf("clang"),
            neededBy = Need.BUILD,
        ),

        // BUILD: Version Control & Transfer Tools
        SystemRequirement(
            id = "git",
            check = RequirementCheck.Command("git"),
            aptPackages = listOf("git"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "curl",
            check = RequirementCheck.Command("curl"),
            aptPackages = listOf("curl"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "rsync",
            check = RequirementCheck.Command("rsync"),
            aptPackages = listOf("rsync"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "tar",
            check = RequirementCheck.Command("tar"),
            aptPackages = listOf("tar"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "file",
            check = RequirementCheck.Command("file"),
            aptPackages = listOf("file"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "xxd",
            check = RequirementCheck.Command("xxd"),
            aptPackages = listOf("xxd"),
            neededBy = Need.BUILD,
        ),

        // BUILD: Archive Tools
        SystemRequirement(
            id = "zip",
            check = RequirementCheck.Command("zip"),
            aptPackages = listOf("zip"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "unzip",
            check = RequirementCheck.Command("unzip"),
            aptPackages = listOf("unzip"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "brotli",
            check = RequirementCheck.Command("brotli"),
            aptPackages = listOf("brotli"),
            neededBy = Need.BUILD,
        ),

        // RUNTIME: Filesystem & Kernel Tools
        SystemRequirement(
            id = "fuse3",
            check = RequirementCheck.Package("fuse3"),
            aptPackages = listOf("fuse3"),
            neededBy = Need.RUNTIME,
        ),
        SystemRequirement(
            id = "libfuse3-dev",
            check = RequirementCheck.Package("libfuse3-dev"),
            aptPackages = listOf("libfuse3-dev"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "attr",
            check = RequirementCheck.Command("attr"),
            aptPackages = listOf("attr"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "ccache",
            check = RequirementCheck.Command("ccache"),
            aptPackages = listOf("ccache"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "openssl",
            check = RequirementCheck.Command("openssl"),
            aptPackages = listOf("openssl"),
            neededBy = Need.BUILD,
        ),

        // RUNTIME: Python & Cryptography
        SystemRequirement(
            id = "python3",
            check = RequirementCheck.Command("python3"),
            aptPackages = listOf("python3"),
            neededBy = Need.RUNTIME,
        ),
        SystemRequirement(
            id = "python-crypto",
            check = RequirementCheck.PythonImport(listOf("cryptography", "pyasn1")),
            aptPackages = listOf("python3-cryptography", "python3-pyasn1"),
            neededBy = Need.RUNTIME,
        ),

        // BUILD: Native Dev Libraries from NativeLibrariesInspector
        SystemRequirement(
            id = "pkg-config",
            check = RequirementCheck.Command("pkg-config"),
            aptPackages = listOf("pkg-config"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "protobuf-compiler",
            check = RequirementCheck.Command("protoc"),
            aptPackages = listOf("protobuf-compiler"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "libprotobuf-dev",
            check = RequirementCheck.Package("libprotobuf-dev"),
            aptPackages = listOf("libprotobuf-dev"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "libbrotli-dev",
            check = RequirementCheck.Package("libbrotli-dev"),
            aptPackages = listOf("libbrotli-dev"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "liblz4-dev",
            check = RequirementCheck.Package("liblz4-dev"),
            aptPackages = listOf("liblz4-dev"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "libpcre2-dev",
            check = RequirementCheck.Package("libpcre2-dev"),
            aptPackages = listOf("libpcre2-dev"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "libzstd-dev",
            check = RequirementCheck.Package("libzstd-dev"),
            aptPackages = listOf("libzstd-dev"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "libusb-1.0-0-dev",
            check = RequirementCheck.Package("libusb-1.0-0-dev"),
            aptPackages = listOf("libusb-1.0-0-dev"),
            neededBy = Need.BUILD,
        ),
        SystemRequirement(
            id = "uuid-dev",
            check = RequirementCheck.Package("uuid-dev"),
            aptPackages = listOf("uuid-dev"),
            neededBy = Need.BUILD,
        ),
    )

    public val ALL_APT_PACKAGES: Set<String> = REQUIREMENTS.flatMap { it.aptPackages }.toSet()

    public val APPROVED_APT_PACKAGES: Map<String, List<String>> = mapOf(
        "host_compilers" to listOf("build-essential", "cmake", "make", "clang"),
        "host_libraries" to listOf(
            "pkg-config", "protobuf-compiler", "libprotobuf-dev", "libbrotli-dev",
            "liblz4-dev", "libpcre2-dev", "libzstd-dev", "libusb-1.0-0-dev", "uuid-dev",
        ),
        "python_crypto" to listOf("python3", "python3-cryptography", "python3-pyasn1"),
        "jdk_dual" to listOf("openjdk-17-jdk", "openjdk-21-jdk"),
        "selinux_attr" to listOf("attr"),
        "archive_tools" to listOf("zip", "unzip", "brotli"),
        "transfer_tools" to listOf("curl", "git", "rsync", "tar", "file", "xxd"),
        "dev_fuse" to listOf("fuse3", "libfuse3-dev"),
        "openssl_tools" to listOf("openssl"),
        "build_cache" to listOf("ccache"),
    )

    public fun isPackageApproved(packageName: String): Boolean = packageName in ALL_APT_PACKAGES

    public fun getRequirement(id: String): SystemRequirement? = REQUIREMENTS.firstOrNull { it.id == id }
}
