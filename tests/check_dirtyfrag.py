"""Verify real KO relocation preserves ELF metadata and complete handoff paths."""
import importlib.util
import pathlib
import struct
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('relocate', ROOT / 'tools/relocate_dirtyfrag.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def sections(data):
    off = struct.unpack_from('<Q', data, 40)[0]
    size, count = struct.unpack_from('<HH', data, 58)
    return [data[off + i * size:off + (i + 1) * size] for i in range(count)]


modules = list((ROOT / 'app/src/main/jni/dfroot/ko').glob('*.ko'))
assert len(modules) == 8
for path in modules:
    original = path.read_bytes()
    relocated = m.relocate(original)
    assert relocated[:64] == original[:64], path
    assert sections(relocated) == sections(original), path
    assert m.PREFIX not in relocated, path
    pos = original.index(m.PREFIX)
    end = original.index(0, pos)
    assert relocated[:pos] == original[:pos], path
    assert relocated[end:] == original[end:], path
    assert relocated[pos:end].split(b'\0')[0] == m.COMMAND, path
    print(path.name + ': ELF layout and handoff paths verified')

if len(sys.argv) > 1:
    with zipfile.ZipFile(sys.argv[1]) as apk:
        for name in ['libdfroot.so', 'libbootstrap.so']:
            info = apk.getinfo('lib/arm64-v8a/' + name)
            data = apk.read(info)
            assert data[:6] == b'\x7fELF\x02\x01', name
            assert struct.unpack_from('<H', data, 18)[0] == 183, name
            assert b'/data/user_de/0/com.lijinmu.arashi/' in data, name
            assert b'roro.stellar.yuehong' not in data, name
            assert m.PREFIX not in data, name
        print('APK contains both arm64 executable payloads with Arashi paths')
