# Sourcing canonical lti completion first
if command -v lti >/dev/null 2>&1; then
    eval "$(env _LTI_COMPLETE=bash_source lti)"
fi

# Delegate completions for launcher aliases
complete -F _lti_tools_aapt2 lti-aapt2
complete -F _lti_tools_adb lti-adb
complete -F _lti_tools_apktool lti-apktool
complete -F _lti_tools_append2simg lti-append2simg
complete -F _lti_tools_avbtool lti-avbtool
complete -F _lti_tools_dump.erofs lti-dump.erofs
complete -F _lti_tools_e2fsdroid lti-e2fsdroid
complete -F _lti_tools_erofsfuse lti-erofsfuse
complete -F _lti_tools_ext2simg lti-ext2simg
complete -F _lti_tools_fastboot lti-fastboot
complete -F _lti_tools_fec lti-fec
complete -F _lti_tools_fsck.erofs lti-fsck.erofs
complete -F _lti_tools_gh lti-gh
complete -F _lti_tools_img2sdat lti-img2sdat
complete -F _lti_tools_img2simg lti-img2simg
complete -F _lti_tools_lpadd lti-lpadd
complete -F _lti_tools_lpdump lti-lpdump
complete -F _lti_tools_lpflash lti-lpflash
complete -F _lti_tools_lpmake lti-lpmake
complete -F _lti_tools_lpunpack lti-lpunpack
complete -F _lti_tools_make_f2fs lti-make_f2fs
complete -F _lti_tools_mkbootfs lti-mkbootfs
complete -F _lti_tools_mkbootimg lti-mkbootimg
complete -F _lti_tools_mkdtboimg lti-mkdtboimg
complete -F _lti_tools_mke2fs lti-mke2fs
complete -F _lti_tools_mkf2fsuserimg lti-mkf2fsuserimg
complete -F _lti_tools_mkfs.erofs lti-mkfs.erofs
complete -F _lti_tools_mkuserimg_mke2fs lti-mkuserimg_mke2fs
complete -F _lti_tools_payload_dumper_go lti-payload-dumper-go
complete -F _lti_tools_repack_bootimg lti-repack_bootimg
complete -F _lti_tools_signapk lti-signapk
complete -F _lti_tools_simg2img lti-simg2img
complete -F _lti_tools_sload_f2fs lti-sload_f2fs
complete -F _lti_tools_unpack_bootimg lti-unpack_bootimg
complete -F _lti_tools_zipalign lti-zipalign
