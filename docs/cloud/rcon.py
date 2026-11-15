import socket, struct, sys
def pkt(i,t,b): d=struct.pack('<ii',i,t)+b.encode()+b'\x00\x00'; return struct.pack('<i',len(d))+d
def rd(s):
    n=struct.unpack('<i',s.recv(4))[0]; d=b''
    while len(d)<n: d+=s.recv(n-len(d))
    return d[8:-2].decode('utf-8','replace')
s=socket.create_connection(('127.0.0.1',25575)); s.sendall(pkt(1,3,'vadim')); rd(s)
for c in sys.argv[1:]:
    s.sendall(pkt(2,2,c)); print('>',c,'\n ',rd(s))
