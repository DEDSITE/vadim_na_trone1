import subprocess, numpy as np, json, sys, os
U='/mnt/user-data/uploads/'
SR=44100
def load(f, ch):
    raw=subprocess.run(['ffmpeg','-v','error','-i',U+f,'-ac',str(ch),'-ar',str(SR),'-f','f32le','-'],capture_output=True).stdout
    return np.frombuffer(raw,np.float32).reshape(-1,ch).copy()
def lufs(path):
    r=subprocess.run(['ffmpeg','-hide_banner','-i',path,'-af','loudnorm=print_format=json','-f','null','-'],capture_output=True,text=True).stderr
    j=json.loads(r[r.rfind('{'):r.rfind('}')+1]); return float(j['input_i']), float(j['input_tp'])
def loop(x, xf):
    n=int(xf*SR); L=len(x)
    y=x[:L-n].copy()
    t=np.linspace(0,1,n)[:,None]
    y[:n]=x[:n]*np.sqrt(t)+x[L-n:]*np.sqrt(1-t)
    return y
def fade(x, fi=0.005, fo=0.02):
    a=int(fi*SR); b=int(fo*SR)
    if a: x[:a]*=np.linspace(0,1,a)[:,None]
    if b: x[-b:]*=np.linspace(1,0,b)[:,None]
    return x
def save(name, x, target):
    tmp=name+'.tmp.wav'
    subprocess.run(['ffmpeg','-v','error','-y','-f','f32le','-ar',str(SR),'-ac',str(x.shape[1]),'-i','-',tmp],input=x.astype(np.float32).tobytes())
    i,tp=lufs(tmp)
    g=target-i
    af=f'volume={g}dB'
    if g > -1.0-tp: af+=',alimiter=limit=0.85:attack=1:release=40:level=false'   # пики срезаем лимитером, а не громкость
    subprocess.run(['ffmpeg','-v','error','-y','-i',tmp,'-af',af,'-ar','44100','-c:a','libvorbis','-q:a','5',f'out/{name}.ogg'])
    subprocess.run(['ffmpeg','-v','error','-y','-i',tmp,'-af',af,'-ar','44100','-b:a','192k',f'listen/{name}.mp3'])
    os.remove(tmp)
    i2,tp2=lufs(f'out/{name}.ogg')
    print(f'{name:22s} {len(x)/SR:6.2f}s ch={x.shape[1]} LUFS {i:6.1f} -> {i2:6.1f} (tp {tp2:5.1f})')
os.makedirs('out',exist_ok=True); os.makedirs('listen',exist_ok=True)
F={k:[f for f in os.listdir(U) if f.startswith(k)][0] for k in ['ab9346f8','6b948163','365264d9','c9a0a0c3','4f448561','719943e5','678833fd','cbfc6ee9','f996a0d2','87273187','7d66b913','9d4f0ca7','b0af4853','280ec58d']}
# фоны (стерео, без позиции) — бесшовные петли
save('zavod_city',    loop(load(F['6b948163'],2),2.0), -30)
save('zavod_wind',    loop(load(F['4f448561'],2),2.0), -26)
save('zavod_factory', loop(load(F['365264d9'],2),2.0), -27)
v=load(F['678833fd'],2)[:int(62*SR)]
save('zavod_vent',    loop(v,2.0), -30)
# позиционные (моно)
save('zavod_servers', loop(load(F['c9a0a0c3'],1),0.8), -24)
fl=load(F['9d4f0ca7'],1)[:int(42*SR)]
save('zavod_lamp_flicker', loop(fl,1.0), -26)
save('zavod_lamp_cycle', fade(load(F['cbfc6ee9'],1)), -22)
save('zavod_spark',   fade(load(F['719943e5'],1)), -20)
save('zavod_debris',  fade(load(F['7d66b913'],1),0.002,0.3), -22)
save('zavod_gas',     fade(load(F['b0af4853'],1),0.05,0.8), -20)
save('zavod_chain_snap', fade(load(F['280ec58d'],1),0.001,0.05), -16)
save('zavod_hatch_move', fade(load(F['87273187'],1),0.002,0.05), -18)
save('zavod_hatch_slam_bed', fade(load(F['ab9346f8'],1),0.002,0.2), -18)
save('zavod_hatch_slam', fade(load(F['f996a0d2'],1),0.001,0.1), -14)

# ---- этап 3а v2: мигание ламп вывески нарезано из одного файла: короткие всплески + ровный гул
if __name__ == '__main__' and os.environ.get('V2'):
    pass
