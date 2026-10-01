package io.ltirom.tooling.core

public enum class ToolId(public val logicalName: String) {
    ADB("adb"),
    FASTBOOT("fastboot"),
    AAPT2("aapt2"),
    AVBTOOL("avbtool"),
    APKTOOL("apktool"),
    SIGNAPK("signapk"),
    ZIPALIGN("zipalign"),
    LPMAKE("lpmake"),
    LPDUMP("lpdump"),
    LPUNPACK("lpunpack"),
    LPADD("lpadd"),
    LPFLASH("lpflash"),
    MKFS_EROFS("mkfs.erofs"),
    DUMP_EROFS("dump.erofs"),
    FSCK_EROFS("fsck.erofs"),
    EROFSFUSE("erofsfuse"),
    IMG2SDAT("img2sdat"),
    PAYLOAD_DUMPER_GO("payload-dumper-go"),
    GH("gh"),
    MKBOOTIMG("mkbootimg"),
    MKDTBOIMG("mkdtboimg"),
    UNPACK_BOOTIMG("unpack_bootimg"),
    REPACK_BOOTIMG("repack_bootimg"),
    MKBOOTFS("mkbootfs"),
    FEC("fec"),
    IMG2SIMG("img2simg"),
    SIMG2IMG("simg2img"),
    APPEND2SIMG("append2simg"),
    EXT2SIMG("ext2simg"),
    MAKE_F2FS("make_f2fs"),
    SLOAD_F2FS("sload_f2fs"),
    MKUSERIMG_MKE2FS("mkuserimg_mke2fs"),
    MKF2FSUSERIMG("mkf2fsuserimg"),
    E2FSDROID("e2fsdroid"),
    MKE2FS("mke2fs");

    public companion object {
        public fun fromLogicalNameOrNull(name: String): ToolId? {
            return entries.firstOrNull { it.logicalName == name }
        }
    }
}
