"""生成标签 22 别名和附加正文片段夹具，不使用用户词库。"""
from pathlib import Path
import struct
import zipfile

root = Path(__file__).parent
template = (root / 'mobi-compression-1.mobi').read_bytes()
count = struct.unpack_from('>H', template, 76)[0]
offsets = [struct.unpack_from('>I', template, 78 + 8 * n)[0] for n in range(count)] + [len(template)]
original = [template[offsets[n]:offsets[n + 1]] for n in range(count)]


def variable(number):
    data = [number & 127 | 128]
    number >>= 7
    while number:
        data.insert(0, number & 127)
        number >>= 7
    return bytes(data)


def index(entries):
    # 单控制字节：标签 1 为偏移，2 为长度，22 为别名目标序号。
    tagx = b'TAGX' + struct.pack('>II', 28, 1) + bytes([1, 1, 1, 0, 2, 1, 2, 0, 22, 1, 4, 0, 0, 0, 0, 1])
    primary = bytearray(192)
    primary[:4] = b'INDX'
    for position, value in [(4, 192), (24, 1), (28, 65001), (36, len(entries))]:
        struct.pack_into('>I', primary, position, value)
    child = bytearray(192)
    child[:4] = b'INDX'
    struct.pack_into('>I', child, 4, 192)
    struct.pack_into('>I', child, 24, len(entries))
    starts = []
    for word, start, length, target in entries:
        starts.append(len(child))
        label = word.encode()
        child += bytes([len(label)]) + label
        child += b'\x04' + variable(target) if target is not None else b'\x03' + variable(start) + variable(length)
    struct.pack_into('>I', child, 20, len(child))
    child += b'IDXT' + b''.join(struct.pack('>H', start) for start in starts)
    return bytes(primary) + tagx, bytes(child)


def write(name, entries, panel=False):
    records = original.copy()
    metadata = bytearray(records[0])
    first = b'<p>first definition</p>'
    second = '<p>中文规范词释义</p>'.encode()
    text = first + second
    main_size = len(text)
    struct.pack_into('>I', metadata, 44, 0xffffffff)  # 不引用原始词形索引。
    records[3], records[4] = index(entries(len(first), len(second)))
    struct.pack_into('>I', metadata, 108, len(records))
    if panel:
        text += b'<html><body>search panel sentinel</body></html>'
        struct.pack_into('>H', metadata, 194, len(records))
        struct.pack_into('>I', metadata, 196, 2)
        records.append(b'FDST' + struct.pack('>IIIIII', 12, 2, 0, main_size, main_size, len(text)))
    struct.pack_into('>I', metadata, 4, len(text))
    records[0], records[1] = bytes(metadata), text
    header = bytearray(template[:78])
    struct.pack_into('>H', header, 76, len(records))
    offset = 78 + 8 * len(records) + 2
    table = b''
    for n, data in enumerate(records):
        table += struct.pack('>II', offset, n)
        offset += len(data)
    (root / name).write_bytes(header + table + b'\0\0' + b''.join(records))


def normal(first, second):
    # 别名在规范词之前；链式别名也必须保留规范词头。
    return [('异体', 0, 0, 2), ('first', 0, first, None), ('规范词', first, second, None), ('链式别名', 0, 0, 0)]


write('mobi-orth-alias.mobi', normal)
write('mobi-extra-flow.mobi', normal, panel=True)
write('mobi-orth-cycle.mobi', lambda first, second: [('cycle', 0, 0, 0)])
write('mobi-orth-outside.mobi', lambda first, second: [('outside', 0, 0, 1)])

# 只更新上述新增夹具，保留既有夹具字节。
archive = root.parents[2] / 'androidTest/assets/dictionary-fixtures.zip'
names = ['mobi-orth-alias.mobi', 'mobi-extra-flow.mobi', 'mobi-orth-cycle.mobi', 'mobi-orth-outside.mobi']
with zipfile.ZipFile(archive) as package:
    contents = {entry.filename: (entry, package.read(entry)) for entry in package.infolist() if entry.filename not in names}
for name in names:
    entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
    entry.compress_type = zipfile.ZIP_DEFLATED
    contents[name] = (entry, (root / name).read_bytes())
with zipfile.ZipFile(archive, 'w') as package:
    for name in sorted(contents):
        entry, data = contents[name]
        package.writestr(entry, data)
