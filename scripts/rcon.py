"""Send commands to the isolated local Mastery smoke server."""
import os,socket,struct,sys
from pathlib import Path
def exact(sock,count):
    result=b''
    while len(result)<count:
        part=sock.recv(count-len(result))
        if not part:raise ConnectionError('RCON disconnected')
        result+=part
    return result
def packet(sock,request,kind,text):
    data=struct.pack('<ii',request,kind)+text.encode()+b'\0\0'
    sock.sendall(struct.pack('<i',len(data))+data)
def read(sock):
    length=struct.unpack('<i',exact(sock,4))[0]
    data=exact(sock,length)
    request,kind=struct.unpack('<ii',data[:8])
    return request,kind,data[8:-2].decode(errors='replace')
password=os.environ.get('MASTERY_RCON_PASSWORD')
if password is None:
    properties=Path(__file__).resolve().parents[1]/'run-server'/'server.properties'
    if properties.exists():
        password=next((line.split('=',1)[1] for line in properties.read_text(encoding='utf-8').splitlines() if line.startswith('rcon.password=')),None)
if not password:raise RuntimeError('Set MASTERY_RCON_PASSWORD or configure run-server/server.properties')
with socket.create_connection(('127.0.0.1',25579),timeout=10) as sock:
    packet(sock,1,3,password)
    request,kind,text=read(sock)
    if request==-1:raise RuntimeError('RCON authentication failed')
    for index,command in enumerate(sys.argv[1:],2):
        packet(sock,index,2,command)
        request,kind,text=read(sock)
        print(command+'\n'+text)
