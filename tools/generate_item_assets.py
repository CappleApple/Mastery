"""Draw the original 16px skill-book cover used by Minecraft's generated item model."""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/mastery'
PALETTE = {
    '.': (0, 0, 0, 0),
    'o': (36, 24, 23, 255),
    's': (86, 41, 31, 255),
    'h': (154, 87, 48, 255),
    'c': (77, 41, 46, 255),
    'l': (120, 67, 58, 255),
    'g': (211, 169, 89, 255),
    'd': (154, 113, 52, 255),
    'p': (221, 205, 159, 255),
    'q': (157, 137, 99, 255),
    'r': (56, 104, 105, 255),
}
ROWS = [
    '................',
    '..ooooooooooo...',
    '.oshgggggggglo..',
    '.oshgccccccglo..',
    '.oshcccccccclop.',
    '.osdccccccccloq.',
    '.oshcccccccclop.',
    '.oshccccccccloq.',
    '.oshcccccccclop.',
    '.osdccccccccloq.',
    '.oshcccccccclop.',
    '.oshgccccccgloq.',
    '.oshddddddddlop.',
    '.osooooooooooq..',
    '..oppppprppppq..',
    '...oooooroooo...',
]
assert len(ROWS) == 16 and all(len(row) == 16 for row in ROWS)
raw = b''.join(b'\x00' + bytes(value for key in row for value in PALETTE[key]) for row in ROWS)
def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
image = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
target = ROOT / 'textures/item/skill_book.png'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(image)
print(f'Generated {target}')
