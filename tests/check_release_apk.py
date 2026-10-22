"""Check runtime reflection/LSPosed entry points in the actual optimized DEX."""
import struct
import sys
import zipfile


def uleb(data, pos):
    value = 0
    for shift in range(0, 35, 7):
        byte = data[pos]
        pos += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, pos
    raise ValueError('Invalid DEX ULEB128')


def definitions(data):
    if not data.startswith(b'dex\n'):
        raise ValueError('Invalid DEX header')
    strings_count, strings_off, types_count, types_off = struct.unpack_from('<4I', data, 56)
    strings = []
    for index in range(strings_count):
        offset = struct.unpack_from('<I', data, strings_off + index * 4)[0]
        _, start = uleb(data, offset)
        end = data.index(0, start)
        strings.append(data[start:end].decode('utf-8', errors='replace'))
    types = [strings[struct.unpack_from('<I', data, types_off + index * 4)[0]] for index in range(types_count)]
    methods_count, methods_off, classes_count, classes_off = struct.unpack_from('<4I', data, 88)
    methods = []
    for index in range(methods_count):
        owner, _, name = struct.unpack_from('<HHI', data, methods_off + index * 8)
        methods.append((types[owner], strings[name]))
    found = set()
    for index in range(classes_count):
        offset = classes_off + index * 32
        class_index = struct.unpack_from('<I', data, offset)[0]
        found.add((types[class_index], None))
        class_data = struct.unpack_from('<I', data, offset + 24)[0]
        if not class_data:
            continue
        counts = []
        for _ in range(4):
            count, class_data = uleb(data, class_data)
            counts.append(count)
        for _ in range(counts[0] + counts[1]):
            _, class_data = uleb(data, class_data)
            _, class_data = uleb(data, class_data)
        for count in counts[2:]:
            method_index = 0
            for _ in range(count):
                diff, class_data = uleb(data, class_data)
                method_index += diff
                _, class_data = uleb(data, class_data)
                code_off, class_data = uleb(data, class_data)
                if code_off:
                    found.add(methods[method_index])
    return found


with zipfile.ZipFile(sys.argv[1]) as apk:
    entry = apk.read('META-INF/xposed/java_init.list').decode().strip()
    if entry != 'com.lijinmu.arashi.xposed.HookEntry':
        raise AssertionError('Unexpected LSPosed entry: ' + entry)
    found = set()
    for path in apk.namelist():
        if path.startswith('classes') and path.endswith('.dex'):
            found.update(definitions(apk.read(path)))
    required = {
        ('Lcom/lijinmu/arashi/xposed/HookEntry;', '<init>'),
        ('Lcom/lijinmu/arashi/xposed/HookEntry;', 'onModuleLoaded'),
        ('Lcom/lijinmu/arashi/xposed/HookEntry;', 'onPackageReady'),
        ('Lcom/lijinmu/arashi/xposed/HookEntry;', 'onSystemServerStarting'),
        ('Lcom/lijinmu/arashi/Main;', 'isModuleActive'),
        ('Lcom/lijinmu/arashi/xposed/hooks/TcpLoginHooks;', 'hookTcpService'),
        ('Lcom/lijinmu/arashi/xposed/hooks/UninstallHooks;', 'hookSystemServer'),
    }
    missing = required - found
    if missing:
        raise AssertionError('Removed or renamed runtime entry points: ' + repr(missing))
print('Optimized APK LSPosed and activation entry points verified')
