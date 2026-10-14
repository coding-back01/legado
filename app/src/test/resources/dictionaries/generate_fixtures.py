"""生成自建最小词库，用固定正文交叉验证格式读取与资源关联。"""
from pathlib import Path
import base64
import gzip
import struct
import zlib
import zipfile
import io
import wave
import math
import tarfile
import subprocess
from xml.sax.saxutils import quoteattr

root = Path(__file__).parent
def png_chunk(tag, data):
    return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data))
png = b'\x89PNG\r\n\x1a\n' + png_chunk(b'IHDR', struct.pack('>IIBBBBB', 32, 32, 8, 2, 0, 0, 0))
png += png_chunk(b'IDAT', zlib.compress((b'\x00' + b'\x33\x99\xcc' * 32) * 32)) + png_chunk(b'IEND', b'')
audio = io.BytesIO()
with wave.open(audio, 'wb') as sound:
    sound.setnchannels(1)
    sound.setsampwidth(2)
    sound.setframerate(8000)
    sound.writeframes(b''.join(struct.pack('<h', int(math.sin(n * 2 * math.pi * 440 / 8000) * 1200)) for n in range(32000)))
wav = audio.getvalue()
words = [('go', '<p>move 移动</p>'), ('学习', '<p>学习：获得知识。<img src="diagram.png"></p>')]

def mdict(path, entries, resources=False, compressed=True, version=2, encoding=None, mode=None, stylesheet=None):
    tag = 'Library_Data' if resources else 'Dictionary'
    encoding = encoding or ('UTF-16' if resources else 'UTF-8')
    style = (' StyleSheet=' + quoteattr(stylesheet)) if stylesheet else ''
    header = f'<{tag} GeneratedByEngineVersion="{version}.0" RequiredEngineVersion="{version}.0" Encrypted="0" Encoding="{encoding}" Title="自建词库"{style}/>'.encode('utf-16le')
    def block(data, codec=None):
        codec = codec if codec is not None else (mode if mode is not None else (2 if compressed else 0))
        if codec == 1:
            assert 4 <= len(data) <= 238
            payload = bytes([17 + len(data)]) + data + b'\x11\0\0'
        else:
            payload = zlib.compress(data) if codec == 2 else data
        return struct.pack('<I', codec) + struct.pack('>I', zlib.adler32(data)) + payload
    record = b''
    keys = b''
    key_charset = 'utf-16le' if encoding.upper() == 'UTF-16' else encoding.lower()
    number = lambda value: struct.pack('>Q' if version == 2 else '>I', value)
    for word, value in entries:
        keys += number(len(record)) + word.encode(key_charset) + (b'\0\0' if key_charset == 'utf-16le' else b'\0')
        record += value if isinstance(value, bytes) else value.encode(key_charset) + (b'\0\0' if key_charset == 'utf-16le' else b'\0')
    keyblock = block(keys)
    first, last = entries[0][0], entries[-1][0]
    info = number(len(entries)) + struct.pack('>H' if version == 2 else '>B', len(first.encode(key_charset)) // (2 if key_charset == 'utf-16le' else 1))
    info += first.encode(key_charset) + ((b'\0\0' if key_charset == 'utf-16le' else b'\0') if version == 2 else b'')
    info += struct.pack('>H' if version == 2 else '>B', len(last.encode(key_charset)) // (2 if key_charset == 'utf-16le' else 1))
    info += last.encode(key_charset) + ((b'\0\0' if key_charset == 'utf-16le' else b'\0') if version == 2 else b'')
    info += number(len(keyblock)) + number(len(keys))
    info_block = block(info, 2) if version == 2 else info
    key_header = (number(1) + number(len(entries)) + (number(len(info)) if version == 2 else b'') + number(len(info_block)) + number(len(keyblock)))
    # 正文跨两个压缩记录块，用以检测词条跨块边界。
    middle = max(1, len(record) // 2)
    records = [record[:middle], record[middle:]]
    packed = [block(item) for item in records]
    record_header = number(2) + number(len(entries)) + number(32 if version == 2 else 16) + number(sum(map(len, packed)))
    record_info = b''.join(number(len(a)) + number(len(b)) for a, b in zip(packed, records))
    path.write_bytes(struct.pack('>I', len(header)) + header + struct.pack('<I', zlib.adler32(header)) + key_header + (struct.pack('>I', zlib.adler32(key_header)) if version == 2 else b'') + info_block + keyblock + record_header + record_info + b''.join(packed))

mdict(root/'sample.mdx', [('go', words[0][1]), ('went', '@@@LINK=go'), words[1]])
obfuscated = bytearray((root/'sample.mdx').read_bytes())
header_size = struct.unpack_from('>I',obfuscated,0)[0]
index_start = 8+header_size
info_size = struct.unpack_from('>Q',obfuscated,index_start+24)[0]
info_start = index_start+44
salt = bytes(obfuscated[info_start+4:info_start+8])+bytes.fromhex('95360000')
# 独立 BouncyCastle 1.86 开发工具计算的参考值；不把提供者加入应用。
assert salt.hex() == '75a605a595360000'
reference_digest = bytes.fromhex('b39b909e8e753816ba980de33c878983')
previous = 0x36
for n in range(info_size-8):
    value = obfuscated[info_start+8+n]^previous^(n&255)^reference_digest[n%16]
    encoded = ((value>>4)|(value<<4))&255
    obfuscated[info_start+8+n]=encoded
    previous=encoded
header = bytes(obfuscated[4:4+header_size]).decode('utf-16le').replace('Encrypted="0"','Encrypted="2"').encode('utf-16le')
obfuscated[4:4+header_size] = header
struct.pack_into('<I',obfuscated,4+header_size,zlib.adler32(header))
(root/'obfuscated.mdx').write_bytes(obfuscated)
mdict(root/'sample.mdd', [('\\diagram.png', png)], True)
mdict(root/'uncompressed.mdx', words, compressed=False)
mdict(root/'legacy.mdx', words, version=1)
mdict(root/'utf16.mdx', words, encoding='UTF-16')
mdict(root/'gbk.mdx', words, encoding='GBK')
mdict(root/'lzo.mdx', words, mode=1)
mdict(root/'cycle.mdx', [('alpha', '@@@LINK=beta'), ('beta', '@@@LINK=alpha')])
mdict(root/'styled.mdx', [('style', '`1`meaning`2`example')], stylesheet='1\n<b>\n</b>\n2\n<i>\n</i>')
mdict(root/'sample.1.mdd', [('\\tone.wav', wav)], True)
mdict(root/'media.mdx', [('media', '<p>音标 ɡəʊ</p><table><tr><td>meaning</td></tr></table><img src="diagram.png"><p><a href="sound://tone.wav">play</a></p><p><a href="entry://other">other</a></p>'), ('other', '<p>second entry</p><p><a href="entry://missing">missing</a></p>'), ('long', '<p>long start 稀有字 𠀀</p>' + '<p>long definition</p>' * 180 + '<p>long ending sentinel</p>')])
mdict(root/'media.mdd', [('\\diagram.png', png), ('\\tone.wav', wav)], True)
(root/'sample.css').write_text('p { line-height: 1.5; }')

body = b''
index = b''
for word, html in words:
    encoded = html.encode()
    index += word.encode() + b'\0' + struct.pack('>II', len(body), len(encoded))
    body += encoded
(root/'sample.ifo').write_text("StarDict's dict ifo file\nversion=3.0.0\nbookname=自建星际词库\nwordcount=2\nidxfilesize=" + str(len(index)) + '\nsametypesequence=h\n')
(root/'sample.idx.gz').write_bytes(gzip.compress(index, mtime=0))
(root/'sample.dict.dz').write_bytes(gzip.compress(body, mtime=0))
(root/'wide.ifo').write_text("StarDict's dict ifo file\nversion=3.0.0\nbookname=宽偏移词库\nwordcount=2\nidxoffsetbits=64\nsametypesequence=h\n")
(root/'wide.idx').write_bytes(b'go\0' + struct.pack('>QI', 0, len(words[0][1].encode())) + '学习'.encode() + b'\0' + struct.pack('>QI', len(words[0][1].encode()), len(words[1][1].encode())))
(root/'wide.dict').write_bytes(body)
def stardict(name, sequence, records):
    content, index = b'', b''
    for word, value in records:
        index += word.encode() + b'\0' + struct.pack('>II', len(content), len(value))
        content += value
    (root/(name+'.ifo')).write_text("StarDict's dict ifo file\nversion=2.4.2\nbookname=正文类型夹具\nwordcount="+str(len(records))+"\nsametypesequence="+sequence+"\n")
    (root/(name+'.idx')).write_bytes(index)
    (root/(name+'.dict')).write_bytes(content)
stardict('text', 'm', [('plain', '释义\n第二行'.encode())])
stardict('xmlbody', 'x', [('xml', '<ar><k>xml</k><def>释义</def><rref>diagram.png</rref></ar>'.encode())])
stardict('binary', 'hW', [('sound', b'<p>sound entry</p>\0'+wav)])
with zipfile.ZipFile(root/'sample.res.zip', 'w') as resources:
    resources.writestr('diagram.png', png)
(root/'sample.syn').write_bytes(b'went\0' + struct.pack('>I', 0))

(root/'sample.dsl').write_bytes(('\ufeff#NAME "自建 DSL"\n#INDEX_LANGUAGE "English"\n#CONTENTS_LANGUAGE "Chinese"\ngo\nwent\n\t[m1][b]移动[/b][/m]\n学习\n\t获得知识。[s]diagram.png[/s] [ref]go[/ref]\n').encode('utf-16le'))
(root/'sample.dsl.dz').write_bytes(gzip.compress((root/'sample.dsl').read_bytes(), mtime=0))
with zipfile.ZipFile(root/'sample.dsl.files.zip', 'w') as resources:
    resources.writestr('diagram.png', png)
    resources.writestr('tone.wav', wav)
(root/'optional.dsl').write_text('#NAME "别名词库"\n{{多行\n注释}}\nword(s)\n\t[b]definition[/b]\n学习{说明}\n\t获得知识\n')

alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
def dictnumber(number):
    value = ''
    while number:
        value = alphabet[number % 64] + value
        number //= 64
    return value or 'A'
text = 'go\n移动\n学习\n获得知识\n'.encode()
(root/'dictd.dict.dz').write_bytes(gzip.compress(text, mtime=0))
first = len('go\n移动\n'.encode())
(root/'dictd.index').write_text('go\tA\t' + dictnumber(first) + '\n学习\t' + dictnumber(first) + '\t' + dictnumber(len(text)-first) + '\n')

(root/'sample.xdxf').write_text('<xdxf lang_from="eng" lang_to="zho"><full_name>自建 XML 词库</full_name><ar><k>go</k><k>went</k><tr>ɡəʊ</tr><def>移动</def></ar><ar><k>学习</k><def>获得知识。<rref>diagram.png</rref><kref>go</kref></def></ar></xdxf>')
(root/'sample.xdxf.dz').write_bytes(gzip.compress((root/'sample.xdxf').read_bytes(), mtime=0))
(root/'external.xdxf').write_text('<!DOCTYPE xdxf [<!ENTITY remote SYSTEM "file:///etc/passwd">]><xdxf><ar><k>bad</k><def>&remote;</def></ar></xdxf>')

def bglblock(kind, data):
    if len(data) < 12:
        return bytes([((len(data)+4) << 4) | kind]) + data
    width = max(1, (len(data).bit_length()+7)//8)
    return bytes([((width-1) << 4) | kind]) + len(data).to_bytes(width, 'big') + data
blocks = bglblock(3, b'\0\x11\0\0\x80\0') + bglblock(3, b'\0\x01' + '自建 BGL'.encode())
for word, html in words:
    key = word.encode()
    definition = html.encode()
    entry = bytes([len(key)]) + key + len(definition).to_bytes(2, 'big') + definition
    if word == 'go': entry += b'\x04went'
    blocks += bglblock(1, entry)
blocks += bglblock(2, b'\x0bdiagram.png' + png)
(root/'sample.bgl').write_bytes(b'\x12\x34\0\x02\0\x06' + gzip.compress(blocks, mtime=0))
latin = bglblock(3, b'\0\x1a\x41') + bglblock(3, b'\0\x1b\x41')
latin_word, latin_body = 'café'.encode('cp1252'), 'définition'.encode('cp1252')
latin += bglblock(1, bytes([len(latin_word)]) + latin_word + struct.pack('>H', len(latin_body)) + latin_body)
(root/'latin.bgl').write_bytes(b'\x12\x34\0\x01\0\x06'+gzip.compress(latin,mtime=0))
(root/'diagram.png').write_bytes(png)

with zipfile.ZipFile(root/'import.zip','w',compression=zipfile.ZIP_DEFLATED) as archive:
    for name in ['sample.mdx','sample.mdd']:
        archive.writestr(name,(root/name).read_bytes())
with zipfile.ZipFile(root/'escape.zip','w') as archive:
    archive.writestr('../outside.txt','must not escape')
tarbytes=io.BytesIO()
with tarfile.open(fileobj=tarbytes,mode='w') as archive:
    for name in ['sample.mdx','sample.mdd']:
        data=(root/name).read_bytes()
        info=tarfile.TarInfo(name);info.size=len(data);info.mtime=0;info.mode=0o600
        archive.addfile(info,io.BytesIO(data))
(root/'import.tar.gz').write_bytes(gzip.compress(tarbytes.getvalue(),mtime=0))
tarbytes=io.BytesIO()
with tarfile.open(fileobj=tarbytes,mode='w') as archive:
    info=tarfile.TarInfo('link');info.type=tarfile.SYMTYPE;info.linkname='../../outside';archive.addfile(info)
(root/'symlink.tar.gz').write_bytes(gzip.compress(tarbytes.getvalue(),mtime=0))
tarbytes=io.BytesIO()
with tarfile.open(fileobj=tarbytes,mode='w') as archive:
    info=tarfile.TarInfo('huge');info.size=9*1024*1024*1024
    archive.addfile(info)
(root/'oversized.tar.gz').write_bytes(gzip.compress(tarbytes.getvalue(),mtime=0))
tarbytes=io.BytesIO()
with tarfile.open(fileobj=tarbytes,mode='w') as archive:
    for n in range(100001):
        info=tarfile.TarInfo('repeat');info.type=tarfile.DIRTYPE;info.mtime=0;archive.addfile(info)
(root/'too-many.tar.gz').write_bytes(gzip.compress(tarbytes.getvalue(),mtime=0))
def rar_header(kind, flags, rest):
    size=7+len(rest)
    header=struct.pack('<BHH',kind,flags,size)+rest
    return struct.pack('<H',zlib.crc32(header)&0xffff)+header
rar=b'Rar!\x1a\x07\0'+rar_header(0x73,0,b'\0'*6)
for name in ['sample.mdx','sample.mdd']:
    data=(root/name).read_bytes();filename=name.encode()
    fields=struct.pack('<IIBIIBBHI',len(data),len(data),3,zlib.crc32(data),0,20,0x30,len(filename),0o100600)+filename
    rar+=rar_header(0x74,0x8000,fields)+data
rar+=rar_header(0x7b,0,b'')
(root/'import.rar').write_bytes(rar)
subprocess.run(['zip','-q','-P','fixture',str((root/'protected.zip').resolve()),'sample.mdx'],cwd=root,check=True)

def pdb_records(data):
    count = struct.unpack_from('>H', data, 76)[0]
    offsets = [struct.unpack_from('>I', data, 78+8*n)[0] for n in range(count)] + [len(data)]
    return [data[offsets[n]:offsets[n+1]] for n in range(count)]
def pdb_file(template, records):
    header = bytearray(template[:78])
    struct.pack_into('>H', header, 76, len(records))
    offset, table = 78+8*len(records)+2, b''
    for n, data in enumerate(records):
        table += struct.pack('>I',offset) + bytes([0,0,(n>>8)&255,n&255])
        offset += len(data)
    return header+table+b'\0\0'+b''.join(records)
plain_path = root/'mobi-compression-1.mobi'
if plain_path.exists():
    template = plain_path.read_bytes()
    records = pdb_records(template)
    metadata = bytearray(records[0])
    struct.pack_into('>I', metadata, 108, len(records))
    text = bytearray(records[1])
    text[224:258] = b'<img recindex="1"> meaning'.ljust(34,b' ')
    records[0], records[1] = bytes(metadata), bytes(text)
    records.append(png)
    (root/'mobi-resource.prc').write_bytes(pdb_file(template,records))
    # 在首个词条内部拆分文本记录，同时移动所有相关索引指针。
    records = pdb_records(template)
    metadata = bytearray(records[0])
    struct.pack_into('>H',metadata,8,2)
    for position in [40,44,48,52,56,60,64,68,72,76,80,108,112,244]:
        value = struct.unpack_from('>I',metadata,position)[0]
        if value != 0xffffffff and value >= 2: struct.pack_into('>I',metadata,position,value+1)
    records = [bytes(metadata),records[1][:240],records[1][240:]]+records[2:]
    (root/'mobi-cross-record.azw').write_bytes(pdb_file(template,records))
    records=pdb_records(template)
    language_metadata=bytearray(records[0])
    struct.pack_into('>II',language_metadata,96,9,4)
    records[0]=bytes(language_metadata)
    (root/'mobi-languages.mobi').write_bytes(pdb_file(template,records))
    records=pdb_records(template)
    latin_metadata=bytearray(records[0])
    struct.pack_into('>I',latin_metadata,16+12,1252)
    struct.pack_into('>I',latin_metadata,44,0xffffffff)
    records[0]=bytes(latin_metadata)
    original_text=records[1]
    body=bytearray(original_text)
    text_length=struct.unpack_from('>I',records[0],4)[0]
    for position in range(min(len(body),text_length)):
        if body[position]>=128: body[position]=ord('?')
    position=original_text.find('być'.encode())
    assert position>=0
    while position>=0:
        body[position:position+4]=b'caf\xe9'
        position=original_text.find('być'.encode(),position+4)
    records[1]=bytes(body)
    for ordinal in range(2,len(records)):
        record=records[ordinal]
        if record.startswith(b'INDX') and b'ORDT' in record:
            # 仅改动 ORDT 字符映射，保持标签、偏移与词形记录布局。
            records[ordinal]=record.replace(b'\x01\x07',b'\x00\xe9')
    (root/'mobi-latin.mobi').write_bytes(pdb_file(template,records))
    records=pdb_records(template)
    metadata=records[0]
    index=struct.unpack_from('>I',metadata,40)[0]
    record=bytearray(records[index])
    table=record.find(b'TAGX')
    assert table>=0
    table_size=struct.unpack_from('>I',record,table+4)[0]
    for offset in range(table+12,table+table_size,4):
        if record[offset]==2: record[offset]=3
    records[index]=bytes(record)
    (root/'mobi-no-length.mobi').write_bytes(pdb_file(template,records))

# AAPT 会特殊处理以 .gz 结尾的资产；通过固定 ZIP 保留原始压缩字节。
archive = root.parents[2] / 'androidTest/assets/dictionary-fixtures.zip'
archive.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as package:
    for file in sorted(root.iterdir()):
        if file.is_file() and file.suffix not in {'.py', '.md', '.json'}:
            entry = zipfile.ZipInfo(file.name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            package.writestr(entry, file.read_bytes())
