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

import org.ide.lti.core.domain.pipeline.text.BuildPropStamper

/**
 * The A/B OTA package: a full `payload.bin` (update_engine's format, major version 2) of the ROM's partitions, signed
 * with the platform key whose certificate the ROM's otacerts.zip carries. It is a partial update: the firmware
 * partitions (abl, modem...) are not in it, and update_engine copies them to the other slot from the running one.
 * Written as the stock PQ84P01 OTA is: 2 MiB REPLACE/REPLACE_XZ operations (xz without a check, 1 MiB dictionary),
 * Virtual A/B compression with COW size estimates, a version per partition.
 */
public object AbOtaPackage {
    /** Where the build writes the payload and the package's members, under the workspace. */
    public const val DIR: String = "work/package/ab"

    /** The script's name in [DIR]. */
    public const val SCRIPT_NAME: String = "ota_payload.py"

    /**
     * Virtual A/B as the payload declares it, `compression:cow_version:compression_factor`, for a device on
     * Android [sdk]: COW v3 with 64 KiB lz4 operations from Android 15, v2 with lz4 before; `none` without VAB.
     * The ROM's own `ro.virtual_ab.*` properties ([vendorProps], vendor's build.prop) win where they are set, as
     * AOSP's OTA tools read them: `enabled`, `compression.enabled`/`compression.method`, `cow_version`,
     * `compression.factor`.
     */
    public fun vab(virtualAb: Boolean, sdk: Int?, vendorProps: String? = null): String {
        fun prop(key: String) = BuildPropStamper.prop(vendorProps, "ro.virtual_ab.$key")
        if (!(prop("enabled")?.let { it == "true" } ?: virtualAb)) return "none"
        val cow = prop("cow_version")?.toIntOrNull() ?: if ((sdk ?: 0) >= COW_V3_SDK) COW_V3 else COW_V2
        val method = if (prop("compression.enabled") == "false") "none" else prop("compression.method") ?: "lz4"
        val factor = prop("compression.factor")?.toLongOrNull() ?: if (cow >= COW_V3) V3_FACTOR else 0L
        return "$method:$cow:$factor"
    }

    private const val COW_V2 = 2
    private const val COW_V3 = 3
    private const val V3_FACTOR = 65536L

    private const val COW_V3_SDK = 35

    /**
     * `python3 SCRIPT_NAME payload|zip|props|verify ...`: the payload writer, the stored-member zip, filling the
     * property-file offsets of the signed zip, and checking the signed zip as a device would. Python 3 standard
     * library and openssl only (both Setup requirements).
     */
    public val SCRIPT: String = """
        # A/B OTA payload writer (update_engine format, major version 2, full operations).
        # python3 ota_payload.py payload OUT_DIR KEY.pem TIMESTAMP SPL GROUP:SIZE VAB name=path[:dynamic] ...
        #   VAB is none, or compression:cow_version:compression_factor (lz4:3:65536).
        # python3 ota_payload.py zip OUT.zip DIR METADATA_FILE        (stored members, metadata first)
        # python3 ota_payload.py props ZIP METADATA_FILE              (fills ota-property-files from the signed zip)
        # python3 ota_payload.py verify ZIP CERT.pem                  (payload_properties and both signatures)
        import base64, hashlib, lzma, os, struct, subprocess, sys, tempfile, zipfile
        from concurrent.futures import ProcessPoolExecutor

        BLOCK = 4096
        CHUNK = 512 * BLOCK  # 2 MiB per operation, as AOSP writes full payloads
        REPLACE, REPLACE_XZ = 0, 8
        XZ_FILTERS = [{'id': lzma.FILTER_LZMA2, 'preset': 6, 'dict_size': 1 << 20}]  # stock: no check, 1 MiB dictionary
        RESERVED = 384  # width of each ota-property-files value, so the zip layout is the same before and after filling


        def varint(n):
            out = bytearray()
            while True:
                b = n & 0x7F
                n >>= 7
                if n:
                    out.append(b | 0x80)
                else:
                    out.append(b)
                    return bytes(out)


        def key(field, wire):
            return varint(field << 3 | wire)


        def u(field, n):
            return key(field, 0) + varint(n)


        def ld(field, data):
            return key(field, 2) + varint(len(data)) + data


        def fixed32(field, n):
            return key(field, 5) + struct.pack('<I', n)


        def compress(chunk):
            xz = lzma.compress(chunk, format=lzma.FORMAT_XZ, check=lzma.CHECK_NONE, filters=XZ_FILTERS)
            return (REPLACE_XZ, xz) if len(xz) < len(chunk) else (REPLACE, chunk)


        def chunks(path):
            with open(path, 'rb') as f:
                while True:
                    c = f.read(CHUNK)
                    if not c:
                        return
                    yield c


        def compressed(pool, path):
            # In order, with at most a few chunks per worker in memory (map would read the whole image first).
            window, pending = (os.cpu_count() or 4) * 4, []
            for chunk in chunks(path):
                pending.append((chunk, pool.submit(compress, chunk)))
                if len(pending) >= window:
                    c, f = pending.pop(0)
                    yield c, f.result()
            for c, f in pending:
                yield c, f.result()


        def sign(key_path, digest):
            with tempfile.NamedTemporaryFile(delete=False) as t:
                t.write(digest)
            try:
                return subprocess.run(['openssl', 'pkeyutl', '-sign', '-inkey', key_path, '-pkeyopt', 'digest:sha256',
                                       '-in', t.name], check=True, capture_output=True).stdout
            finally:
                os.unlink(t.name)


        def signatures(sig):
            return ld(1, ld(2, sig) + fixed32(3, len(sig)))


        def payload(out_dir, key_path, timestamp, spl, group, vab, specs):
            group_name, group_size = group.rsplit(':', 1)
            sig_len = len(sign(key_path, b'\0' * 32))
            blobs_path = os.path.join(out_dir, 'payload.blobs')
            partitions, dynamic, offset = [], [], 0
            with open(blobs_path, 'wb') as blobs, ProcessPoolExecutor() as pool:
                for spec in specs:
                    name, rest = spec.split('=', 1)
                    path, _, kind = rest.partition(':')
                    size = os.path.getsize(path)
                    if size % BLOCK:
                        raise SystemExit(f'{name}: {size} bytes is not a whole number of {BLOCK}-byte blocks')
                    whole, ops, block = hashlib.sha256(), b'', 0
                    for chunk, (op_type, data) in compressed(pool, path):
                        whole.update(chunk)
                        blocks = len(chunk) // BLOCK
                        ops += ld(8, u(1, op_type) + u(2, offset) + u(3, len(data)) + ld(6, u(1, block) + u(2, blocks))
                                  + ld(8, hashlib.sha256(data).digest()))
                        blobs.write(data)
                        offset += len(data)
                        block += blocks
                    update = ld(1, name.encode()) + ld(7, u(1, size) + ld(2, whole.digest())) + ops + ld(17, timestamp.encode())
                    if kind == 'dynamic':
                        dynamic.append(name)
                        if vab != 'none':
                            # Blocks per COW op: the compression factor (COW v3), else one (v2 has none).
                            factor = max(1, int(vab.split(':')[2]) // BLOCK)
                            # Full data, nothing compresses in the COW worse than raw: its size plus room for its ops.
                            update += u(19, size + (4 << 20) + (block // factor + 1) * 64) + u(20, -(-block // factor) + 32)
                    partitions.append(ld(13, update))
                    print(f'{name}: {size} bytes, {block} blocks', flush=True)
            meta = ld(1, ld(1, group_name.encode()) + u(2, int(group_size)) + b''.join(ld(3, n.encode()) for n in dynamic))
            if vab != 'none':
                compression, cow, factor = vab.split(':')
                meta += u(2, 1) + u(3, int(compression != 'none')) + ld(4, compression.encode()) + u(5, int(cow))
                if int(factor):
                    meta += u(7, int(factor))
            sig_size = len(signatures(b'\0' * sig_len))
            manifest = (u(3, BLOCK) + u(4, offset) + u(5, sig_size) + u(12, 0) + b''.join(partitions)
                        + u(14, int(timestamp)) + ld(15, meta) + u(16, 1) + ld(18, spl.encode()))
            header = b'CrAU' + struct.pack('>QQI', 2, len(manifest), sig_size)
            metadata = header + manifest
            metadata_sig = signatures(sign(key_path, hashlib.sha256(metadata).digest()))
            assert len(metadata_sig) == sig_size
            out = os.path.join(out_dir, 'payload.bin')
            whole = hashlib.sha256()
            with open(out, 'wb') as f:
                for part in (metadata, metadata_sig):
                    f.write(part)
                    whole.update(part)
                with open(blobs_path, 'rb') as b:
                    for block in iter(lambda: b.read(1 << 20), b''):
                        f.write(block)
                        whole.update(block)
                payload_sig = signatures(sign(key_path, whole.digest()))
                assert len(payload_sig) == sig_size
                f.write(payload_sig)
            os.unlink(blobs_path)
            file_hash = hashlib.sha256()
            with open(out, 'rb') as f:
                for block in iter(lambda: f.read(1 << 20), b''):
                    file_hash.update(block)
            with open(os.path.join(out_dir, 'payload_properties.txt'), 'w') as p:
                p.write(f'FILE_HASH={base64.b64encode(file_hash.digest()).decode()}\n'
                        f'FILE_SIZE={os.path.getsize(out)}\n'
                        f'METADATA_HASH={base64.b64encode(hashlib.sha256(metadata).digest()).decode()}\n'
                        f'METADATA_SIZE={len(metadata)}\n')
            print(f'payload.bin {os.path.getsize(out)} bytes, {len(partitions)} partitions', flush=True)


        def write_zip(out, directory, metadata_file):
            names = ['payload.bin', 'payload_properties.txt']
            with zipfile.ZipFile(out, 'w', zipfile.ZIP_STORED, allowZip64=True) as z:
                z.write(metadata_file, 'META-INF/com/android/metadata')
                for n in names:
                    z.write(os.path.join(directory, n), n)


        def data_span(z, name):
            info = z.getinfo(name)
            z.fp.seek(info.header_offset)
            local = z.fp.read(30)
            name_len, extra_len = struct.unpack('<HH', local[26:30])
            return info.header_offset + 30 + name_len + extra_len, info.compress_size


        def props(zip_path, metadata_file):
            with zipfile.ZipFile(zip_path) as z:
                spans = {n: data_span(z, n) for n in ('payload.bin', 'payload_properties.txt', 'META-INF/com/android/metadata')}
                # payload_metadata.bin: the payload's header, manifest and metadata signature, which a streaming
                # client reads first.
                z.fp.seek(spans['payload.bin'][0])
                head = z.fp.read(24)
            _, manifest_size = struct.unpack('>QQ', head[4:20])
            meta_size = 24 + manifest_size + struct.unpack('>I', head[20:24])[0]
            streaming = ','.join(f'{n.rsplit("/", 1)[-1]}:{o}:{s}' for n, (o, s) in spans.items())
            values = {
                'ota-property-files': f'payload_metadata.bin:{spans["payload.bin"][0]}:{meta_size},{streaming}',
                'ota-streaming-property-files': streaming,
            }
            lines = open(metadata_file).read().splitlines()
            out = [f'{k}={values[k].ljust(RESERVED)}' if k in values else line
                   for line in lines for k in [line.split('=', 1)[0]]]
            value = values['ota-property-files']
            text = '\n'.join(out) + '\n'
            changed = text != open(metadata_file).read()
            with open(metadata_file, 'w') as f:
                f.write(text)
            print('changed' if changed else 'unchanged', value)


        def fields(b):
            i, out = 0, {}
            while i < len(b):
                k, i = read_varint(b, i)
                field, wire = k >> 3, k & 7
                if wire == 0:
                    v, i = read_varint(b, i)
                elif wire == 5:
                    v, i = struct.unpack_from('<I', b, i)[0], i + 4
                elif wire == 2:
                    n, i = read_varint(b, i)
                    v, i = b[i:i + n], i + n
                else:
                    raise ValueError(f'wire type {wire}')
                out.setdefault(field, []).append(v)
            return out


        def read_varint(b, i):
            r = s = 0
            while True:
                c = b[i]
                i += 1
                r |= (c & 0x7F) << s
                s += 7
                if c < 0x80:
                    return r, i


        def verify(zip_path, cert):
            # The signed zip as a device reads it: payload_properties matches payload.bin, both signatures check out.
            pub = subprocess.run(['openssl', 'x509', '-in', cert, '-pubkey', '-noout'], check=True, capture_output=True).stdout
            with tempfile.TemporaryDirectory() as t, zipfile.ZipFile(zip_path) as z:
                open(os.path.join(t, 'pub.pem'), 'wb').write(pub)
                start, size = data_span(z, 'payload.bin')
                props_text = z.read('payload_properties.txt').decode()
                f = z.fp
                f.seek(start)
                head = f.read(24)
                _, manifest_size = struct.unpack('>QQ', head[4:20])
                sig_size = struct.unpack('>I', head[20:24])[0]
                manifest = f.read(manifest_size)
                metadata_sig = f.read(sig_size)
                m = fields(manifest)
                whole = hashlib.sha256(head + manifest + metadata_sig)
                left = m[4][0]
                while left:
                    b = f.read(min(left, 1 << 20))
                    whole.update(b)
                    left -= len(b)
                payload_sig = f.read(m[5][0])
                file_hash = whole.copy()
                file_hash.update(payload_sig)
                for name, digest, sig in (('metadata', hashlib.sha256(head + manifest).digest(), metadata_sig),
                                          ('payload', whole.digest(), payload_sig)):
                    raw = fields(fields(sig)[1][0])[2][0]
                    open(os.path.join(t, 'd'), 'wb').write(digest)
                    open(os.path.join(t, 's'), 'wb').write(raw)
                    r = subprocess.run(['openssl', 'pkeyutl', '-verify', '-pubin', '-inkey', os.path.join(t, 'pub.pem'),
                                        '-pkeyopt', 'digest:sha256', '-in', os.path.join(t, 'd'), '-sigfile',
                                        os.path.join(t, 's')], capture_output=True)
                    if r.returncode:
                        raise SystemExit(f'{name} signature does not verify with {cert}')
                expected = f'FILE_HASH={base64.b64encode(file_hash.digest()).decode()}'
                if expected not in props_text or f'FILE_SIZE={size}' not in props_text:
                    raise SystemExit('payload_properties.txt does not describe payload.bin')
            print(f'verified: {len(m.get(13, []))} partitions, both signatures, {size} bytes')


        if __name__ == '__main__':
            cmd, args = sys.argv[1], sys.argv[2:]
            if cmd == 'payload':
                payload(args[0], args[1], args[2], args[3], args[4], args[5], args[6:])
            elif cmd == 'zip':
                write_zip(args[0], args[1], args[2])
            elif cmd == 'props':
                props(args[0], args[1])
            elif cmd == 'verify':
                verify(args[0], args[1])
            else:
                raise SystemExit(f'unknown command {cmd}')
    """.trimIndent()
}
