"""Generate Mastery's original pixel-art GUI sprites using only the Python standard library.

These source assets are replaceable through resource packs. No Minecraft art is copied.
"""
from pathlib import Path
import json
import random
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/mastery/textures/gui/sprites/graph"
ROOT.mkdir(parents=True, exist_ok=True)

def image(w, h, color=(0,0,0,0)):
    return [[color for _ in range(w)] for _ in range(h)]

def rect(p, x0, y0, x1, y1, c):
    for y in range(max(0,y0), min(len(p),y1)):
        for x in range(max(0,x0), min(len(p[0]),x1)): p[y][x] = c

def border(p, inset, light, dark):
    w,h=len(p[0]),len(p)
    rect(p,inset,inset,w-inset,inset+1,light)
    rect(p,inset,inset,inset+1,h-inset,light)
    rect(p,inset,h-inset-1,w-inset,h-inset,dark)
    rect(p,w-inset-1,inset,w-inset,h-inset,dark)

def save(name, p, mode=None):
    h,w=len(p),len(p[0])
    raw = b"".join(b"\0" + bytes(v for pixel in row for v in pixel) for row in p)
    def chunk(t,d): return struct.pack(">I",len(d))+t+d+struct.pack(">I",zlib.crc32(t+d)&0xffffffff)
    (ROOT/(name+".png")).write_bytes(b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",w,h,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(raw,9))+chunk(b"IEND",b""))
    if mode:
        scaling={"type":mode,"width":w,"height":h}
        if mode=="nine_slice": scaling["border"]=4
        (ROOT/(name+".png.mcmeta")).write_text(json.dumps({"gui":{"scaling":scaling}},indent=2)+"\n")

def framed(name, accent):
    p=image(24,24,(47,43,36,255))
    border(p,0,(20,18,15,255),(12,11,9,255))
    border(p,1,(163,148,117,255),(54,47,35,255))
    border(p,2,accent,(max(0,accent[0]-80),max(0,accent[1]-80),max(0,accent[2]-80),255))
    border(p,3,(73,67,54,255),(34,30,24,255))
    for y in range(5,19):
        for x in range(5,19):
            if (x+y*3)%13==0:p[y][x]=(51,47,39,255)
    for x,y in [(2,2),(21,2),(2,21),(21,21)]:p[y][x]=(222,204,154,255)
    save(name,p,"nine_slice")

for name,accent in {
    "organizational":(164,156,132,255),"specialization":(211,173,91,255),
    "active":(153,179,198,255),"passive":(153,162,120,255),"modifier":(158,132,172,255),
    "synergy":(198,128,77,255),"keystone":(215,169,76,255),"utility":(139,170,153,255),
    "panel":(123,119,106,255)
}.items():framed(name,accent)

rng=random.Random(738)
p=image(32,32)
for y in range(32):
    for x in range(32):
        n=rng.randrange(0,8); p[y][x]=(24+n,24+n,23+n,255)
        if y%16==0 or (x+(16 if y>=16 else 0))%32==0:p[y][x]=(18,18,17,255)
save("background",p,"tile")

for name,c in [("selected",(255,215,125,255)),("hover",(220,211,183,255))]:
    p=image(24,24)
    border(p,0,(20,18,13,240),(20,18,13,240))
    border(p,1,c,c)
    for x,y in [(2,2),(21,2),(2,21),(21,21)]:p[y][x]=c
    save(name,p,"nine_slice")

p=image(24,24)
border(p,2,(130,125,113,120),(51,47,39,110))
rect(p,21,2,24,4,(47,42,31,220));p[0][22]=p[1][21]=p[1][23]=(174,157,119,240)
save("locked",p,"nine_slice")
p=image(12,12,(58,41,21,255))
border(p,0,(246,208,119,255),(90,66,30,255));border(p,1,(172,125,53,255),(114,77,31,255))
save("point_badge",p,"nine_slice")

for name,c in [("xp_background",(22,27,17,255)),("xp_bar",(127,176,63,255))]:
    p=image(8,3,c);rect(p,0,0,8,1,(min(255,c[0]+30),min(255,c[1]+30),min(255,c[2]+15),255))
    save(name,p,"tile")
p=image(8,3,(119,111,87,255));rect(p,0,0,8,1,(170,157,120,255));save("connector",p,"tile")
p=image(12,5)
rect(p,0,1,8,4,(182,116,63,255));rect(p,0,1,8,2,(228,176,88,255))
p[0][3]=p[4][3]=(228,176,88,255);save("synergy_connector",p,"tile")

for name in ["expand","collapse"]:
    p=image(9,9,(42,36,25,255));border(p,0,(180,160,119,255),(74,59,33,255))
    rect(p,2,4,7,5,(225,210,166,255))
    if name=="expand":rect(p,4,2,5,7,(225,210,166,255))
    save(name,p)
p=image(16,16)
for x,y in [(7,1),(8,1),(6,2),(9,2),(5,3),(10,3),(4,4),(11,4),(4,5),(11,5),(5,6),(10,6),(6,7),(9,7),(7,8),(8,8),(7,9),(8,9),(7,10),(8,10),(7,11),(8,11),(6,12),(9,12),(5,13),(10,13)]:
    p[y][x]=(219,191,121,255)
save("rune",p)
print(f"Generated {len(list(ROOT.glob('*.png')))} GUI sprites in {ROOT}")
