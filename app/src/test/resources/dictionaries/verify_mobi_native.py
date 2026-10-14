"""在主机编译生产 MOBI 桥接层并验证自建兼容夹具。"""
from pathlib import Path
import struct
import subprocess
import tempfile

fixtures = Path(__file__).resolve().parent
repository = fixtures.parents[4]
native = repository / 'app/src/main/cpp'
sources = ['buffer', 'compression', 'debug', 'index', 'memory', 'meta', 'parse_rawml', 'read', 'structure', 'util']

with tempfile.TemporaryDirectory(prefix='legado-mobi-regression-') as temporary:
    work = Path(temporary)
    probe = work / 'probe'
    subprocess.run([
        'cc', '-std=c99', '-DHAVE_STRDUP', '-DHAVE_UNISTD_H', '-DMOBI_INLINE=inline',
        '-DRAWTEXT_SIZEMAX=536870912', '-I' + str(native / 'libmobi'),
        str(repository / 'app/src/test/cpp/mobi_compatibility_test.c'), str(native / 'dictionary_mobi.c'),
        *[str(native / 'libmobi' / (name + '.c')) for name in sources], '-lz', '-o', str(probe)
    ], check=True)
    for name, expected in [('mobi-orth-alias.mobi', 0), ('mobi-extra-flow.mobi', 0),
                           ('mobi-orth-cycle.mobi', 1), ('mobi-orth-outside.mobi', 1)]:
        output = work / name
        output.mkdir()
        result = subprocess.run([str(probe), str(fixtures / name), str(output)], capture_output=True, text=True)
        assert result.returncode == expected, (name, result.stdout, result.stderr)
        if expected == 0:
            data = (output / 'mobi.index').read_bytes()
            position = 8

            def number():
                global position
                value = struct.unpack_from('>I', data, position)[0]
                position += 4
                return value

            def text():
                global position
                size = number()
                value = data[position:position + size].decode()
                position += size
                return value

            text()  # 标题
            count = number()
            text()  # 输入语言
            text()  # 输出语言
            number()  # 词头编码
            number()  # 词形编码
            entries = {}
            order = []
            for _ in range(count):
                start, length, word = number(), number(), text()
                text()  # 词形
                entries[word] = (start, length)
                order.append(word)
            assert entries['异体'] == entries['链式别名'] == (entries['规范词'][0], 0)
            assert order.index('规范词') < order.index('异体')
            assert b'search panel sentinel' not in (output / 'mobi.body').read_bytes()
            if name == 'mobi-extra-flow.mobi':
                assert b'search panel sentinel' in (output / 'flow00001.html').read_bytes()
        print(name, '通过')
