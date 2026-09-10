#!/usr/bin/env python3
"""Rebuild the two authored POC classes; never package supplied game dependencies."""
import argparse
import base64
import hashlib
import io
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def build(classpath):
    with tempfile.TemporaryDirectory(prefix='wurm-poc-build-') as temp:
        classes = Path(temp)
        subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17',
                        '-cp', classpath, '-d', str(classes),
                        str(ROOT/'poc/src/SteamJni/SteamServerApi.java'),
                        str(ROOT/'poc/src/poc/AndroidServerMain.java')], check=True)
        entries = {'META-INF/MANIFEST.MF': b'Manifest-Version: 1.0\r\n\r\n'}
        entries.update({p.relative_to(classes).as_posix(): p.read_bytes()
                        for p in classes.rglob('*.class')})
        if set(entries) != {'META-INF/MANIFEST.MF', 'SteamJni/SteamServerApi.class',
                            'poc/AndroidServerMain.class'}:
            raise RuntimeError('Unexpected POC compiler output; refusing to package')
        output = io.BytesIO()
        with zipfile.ZipFile(output, 'w') as jar:
            for name, data in sorted(entries.items()):
                item = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                item.compress_type = zipfile.ZIP_STORED
                item.external_attr = 0o100644 << 16
                jar.writestr(item, data)
        return output.getvalue()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath', required=True, help='Owned server.jar, optionally with common.jar')
    parser.add_argument('--output', required=True, type=Path, help='Destination .jar.base64 file')
    args = parser.parse_args()
    data = build(args.classpath)
    args.output.write_bytes(base64.encodebytes(data))
    print('POC_SHA256=' + hashlib.sha256(data).hexdigest())
    for name in ['poc/src/poc/AndroidServerMain.java', 'poc/src/SteamJni/SteamServerApi.java']:
        print(name + ' SHA256=' + hashlib.sha256((ROOT/name).read_bytes()).hexdigest())
