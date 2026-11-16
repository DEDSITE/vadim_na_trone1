import json
J={}
def job(name,w,h,html,cut=True): J[name]=(w,h,html,cut)
COL=['#D7263D','#F46F1F','#F2C230','#3FAE49','#3CC6E0','#2F5BD3','#8E44C9','#ECE6D8']
DK=['#8F1626','#A54510','#A7831A','#26722D','#23859A','#1B3A8F','#5D2A87','#A69F8F']
LT=['#F2707F','#FFA66A','#FFE27F','#85DC8C','#94ECFA','#7F9FF0','#C08FF0','#FFFFFF']
INK='#2a1d14'
ICONS=[
 '<path d="M10 30c-5 0-6-7-1-8 0-6 8-8 11-4 3-5 12-3 11 3 6 0 6 9 0 9z" fill="none" stroke="#2a1d14" stroke-width="3" stroke-linejoin="round"/><path d="M14 36l-2 4M22 36v5M30 36l2 4" stroke="#2a1d14" stroke-width="3" stroke-linecap="round"/>',
 '<rect x="11" y="6" width="22" height="16" rx="2" fill="none" stroke="#2a1d14" stroke-width="3"/><circle cx="17" cy="14" r="2.5" fill="#2a1d14"/><circle cx="27" cy="14" r="2.5" fill="#2a1d14"/><rect x="13" y="24" width="18" height="12" fill="none" stroke="#2a1d14" stroke-width="3"/><path d="M13 27H6v8M31 27h7v8M17 36v6M27 36v6M22 2v4" stroke="#2a1d14" stroke-width="3" stroke-linecap="round" fill="none"/>',
 '<path d="M22 14l-12-7v14zM22 14l12-7v14z" fill="none" stroke="#2a1d14" stroke-width="3" stroke-linejoin="round"/><circle cx="22" cy="14" r="3.5" fill="#2a1d14"/><path d="M20 17l-5 22M24 17l5 22" stroke="#2a1d14" stroke-width="3" stroke-linecap="round"/>',
 '<path d="M8 18c0 12 8 18 14 18s14-6 14-18z" fill="none" stroke="#2a1d14" stroke-width="3" stroke-linejoin="round"/><path d="M8 18h28M14 18v5M20 18v6M26 18v6M32 18v5" stroke="#2a1d14" stroke-width="2.5"/><path d="M6 8l4 4M38 8l-4 4M22 4v5" stroke="#2a1d14" stroke-width="3" stroke-linecap="round"/>',
 '<path d="M17 4h10M19 4v12L9 36c-1 3 1 5 4 5h18c3 0 5-2 4-5L25 16V4" fill="none" stroke="#2a1d14" stroke-width="3" stroke-linejoin="round"/><path d="M13 30h18" stroke="#2a1d14" stroke-width="2.5"/>',
 '<circle cx="22" cy="22" r="17" fill="none" stroke="#2a1d14" stroke-width="3"/><circle cx="15" cy="17" r="4.5" fill="none" stroke="#2a1d14" stroke-width="2.5"/><circle cx="30" cy="19" r="2.5" fill="#2a1d14"/><path d="M13 29c4 4 12 4 17-1M24 31v5" stroke="#2a1d14" stroke-width="3" stroke-linecap="round" fill="none"/>',
 '<path d="M8 20c0-9 6-15 14-15s14 6 14 15c0 5-2 8-5 9v6H13v-6c-3-1-5-4-5-9z" fill="none" stroke="#2a1d14" stroke-width="3" stroke-linejoin="round"/><circle cx="16" cy="20" r="4" fill="#2a1d14"/><circle cx="28" cy="20" r="4" fill="#2a1d14"/><path d="M18 35v4M22 35v4M26 35v4" stroke="#2a1d14" stroke-width="2.5"/>',
 '<path d="M8 38V14l6-9 5 8h6l5-8 6 9v24z" fill="none" stroke="#2a1d14" stroke-width="3" stroke-linejoin="round"/><circle cx="16" cy="22" r="2.5" fill="#2a1d14"/><circle cx="28" cy="22" r="2.5" fill="#2a1d14"/><path d="M4 27l9 1M4 32l9-1M40 27l-9 1M40 32l-9-1" stroke="#2a1d14" stroke-width="2.5" stroke-linecap="round" fill="none"/>']
NAMES=["ПЕРДЁЖ","ТРАНСФОРМЕР","МАЛЕНЬКИЕ ДЕВОЧКИ","СМЕХ","АНАЛИЗ","ДОЛБАЁБЫ","КОНЧЕННЫЕ УРОДЫ","МЯУ"]
# ---- плакат «Азбука Вадима»: 256×256, лист 186×256 по центру; кольца под кружки — x=196, y=82+i*20 (центры, px текстуры)
rows=''.join(f'<div style="position:absolute;left:8px;top:{74+i*20}px;width:170px;height:18px;display:flex;align-items:center;gap:6px">'
  f'<svg width="17" height="17" viewBox="0 0 44 44">{ICONS[i]}</svg>'
  f'<div class="b" style="font-size:{9 if len(NAMES[i])>12 else 11}px;color:{INK};letter-spacing:0.2px;white-space:nowrap">{NAMES[i]}</div></div>'
  f'<div style="position:absolute;left:153px;top:{74+i*20+1}px;width:16px;height:16px;border-radius:50%;border:2px solid {INK}"></div>' for i in range(8))
job('poster',256,256,f'''<div style="position:absolute;left:35px;top:0;width:186px;height:256px;background:#e4d6b4;box-shadow:inset 0 0 26px #9c8257,inset 0 0 0 2px #b8a27a;overflow:hidden">
<div style="position:absolute;left:8px;top:7px;font-family:M;font-size:7px;color:#6b5640;letter-spacing:1px">СТУДИЯ «ТРОН» · ПОСОБИЕ № 2</div>
<div class="b" style="position:absolute;left:8px;top:17px;font-size:27px;line-height:23px;color:#a3161f;transform:scaleX(0.86)">АЗБУКА<br>ВАДИМА</div>
<div style="position:absolute;left:8px;top:66px;width:170px;height:2px;background:{INK}"></div>{rows}
<div style="position:absolute;left:8px;top:240px;font-family:M;font-size:6px;color:#6b5640">УТВЕРЖДАЮ: В. (НА ТРОНЕ)</div>
<div class="b" style="position:absolute;left:96px;top:236px;width:50px;height:20px;border:2px solid #a3161f;color:#a3161f;font-size:6px;line-height:7px;text-align:center;transform:rotate(-10deg);padding-top:2px">ЗВУКИ ПРОВЕРЕНЫ<br>НА ВАС</div></div>''')
for c in range(8):
    job(f'dot_{c}',16,16,f'<div style="position:absolute;left:1px;top:1px;width:14px;height:14px;border-radius:50%;background:{COL[c]};border:2px solid {INK}"></div>')
    job(f'key_{c}',16,16,f'<div style="width:16px;height:16px;background:{COL[c]};box-shadow:inset 0 -3px 0 {DK[c]},inset 0 2px 0 {LT[c]},inset 1px 0 0 {LT[c]}"></div>',False)
# ---- пульт ПУ-8 (верх 128×44, читается со стороны стула)
job('console_top',128,44,'''<div style="width:128px;height:44px;background:#1d1d1f;box-shadow:inset 0 0 0 2px #0b0b0c,inset 0 1px 0 3px #3a3a3e;position:relative">
<div style="position:absolute;left:5px;top:3px;font-family:M;font-size:5px;color:#9a9384;letter-spacing:1px">ЭЛЕКТРОНИКА · ПУ-8</div>
<div style="position:absolute;right:6px;top:3px;width:4px;height:4px;border-radius:50%;background:#d7263d;box-shadow:0 0 3px #d7263d"></div>
<div style="position:absolute;left:5px;bottom:2px;font-family:M;font-size:4px;color:#6d675c">НЕ ТРОГАТЬ МОКРЫМИ РУКАМИ   ~220 В</div>
<div style="position:absolute;left:2px;top:2px;width:2px;height:2px;background:#777"></div><div style="position:absolute;right:2px;bottom:2px;width:2px;height:2px;background:#777"></div></div>''',False)
# ---- магнитофон «Вадим-203»
job('tape_front',128,64,'''<div style="width:128px;height:64px;background:#cfc4a8;box-shadow:inset 0 0 0 2px #8f8468;position:relative">
<div class="b" style="position:absolute;left:6px;top:3px;font-size:9px;color:#3a2e22;letter-spacing:1px">ВАДИМ-203</div>
<div style="position:absolute;right:6px;top:5px;font-family:M;font-size:5px;color:#5c5040">СТЕРЕО · 19 СМ/С</div>
<div style="position:absolute;left:6px;top:15px;width:116px;height:32px;background:#2a2622;display:flex;align-items:center;justify-content:space-between;padding:0 6px">
<div style="width:28px;height:28px;border-radius:50%;background:#6f6658;box-shadow:inset 0 0 0 2px #3d3730,inset 0 0 0 7px #8c8270,inset 0 0 0 11px #4a443b"></div>
<div style="flex-grow:1;height:2px;background:#5a3a22;margin:0 3px"></div>
<div style="width:28px;height:28px;border-radius:50%;background:#6f6658;box-shadow:inset 0 0 0 2px #3d3730,inset 0 0 0 5px #8c8270,inset 0 0 0 11px #4a443b"></div></div>
<div style="position:absolute;left:6px;top:50px;width:26px;height:11px;background:#f2e3b8;box-shadow:inset 0 0 0 1px #3a2e22"><div style="position:absolute;left:13px;bottom:1px;width:1px;height:9px;background:#a3161f;transform:rotate(-35deg);transform-origin:bottom"></div></div>
<div style="position:absolute;left:38px;top:53px;font-family:M;font-size:5px;color:#3a2e22">ВОСПР.</div>
<div style="position:absolute;right:6px;top:52px;font-family:M;font-size:4px;color:#5c5040;text-align:right">НЕ ПЕРЕМАТЫВАТЬ</div></div>''',False)
job('tape_top',128,80,'<div style="width:128px;height:80px;background:#c4b99c;box-shadow:inset 0 0 0 2px #8f8468"><div style="position:absolute;left:10px;top:10px;width:108px;height:6px;background:#a59a7e"></div></div>',False)
job('tape_side',16,16,'<div style="width:16px;height:16px;background:#b4a98c;box-shadow:inset 0 0 0 1px #8f8468"></div>',False)
# ---- рубильник: эбонитовая панель 64×96, нож, ручка
job('switch_panel',64,96,'''<div style="width:64px;height:96px;background:#2b2b2e;box-shadow:inset 0 0 0 3px #0e0e10;position:relative">
<div style="position:absolute;left:5px;top:5px;width:6px;height:6px;border-radius:50%;background:#8a8a8e"></div><div style="position:absolute;right:5px;top:5px;width:6px;height:6px;border-radius:50%;background:#8a8a8e"></div>
<div style="position:absolute;left:5px;bottom:5px;width:6px;height:6px;border-radius:50%;background:#8a8a8e"></div><div style="position:absolute;right:5px;bottom:5px;width:6px;height:6px;border-radius:50%;background:#8a8a8e"></div>
<div style="position:absolute;left:20px;top:58px;width:24px;height:18px;background:#b08a3c;box-shadow:inset 0 -3px 0 #6b5420"></div>
<div style="position:absolute;left:23px;top:10px;width:18px;height:8px;background:#b08a3c"></div>
<div style="position:absolute;left:0;bottom:9px;width:64px;text-align:center;font-family:M;font-size:6px;color:#8a7a64">ЗАПИСЬ</div></div>''',False)
job('switch_blade',16,16,'<div style="width:16px;height:16px;background:#c9c9cc;box-shadow:inset 2px 0 0 #ececef,inset -2px 0 0 #8a8a8e"></div>',False)
job('switch_handle',16,16,'<div style="width:16px;height:16px;background:#a3161f;box-shadow:inset 0 3px 0 #e0505a,inset 0 -3px 0 #6b0e14"></div>',False)
job('lamp_on',16,16,'<div style="width:16px;height:16px;background:#ff3b2f;box-shadow:inset 0 0 0 3px #ffb0a8"></div>',False)
job('lamp_off',16,16,'<div style="width:16px;height:16px;background:#4a1a14;box-shadow:inset 0 0 0 3px #6b2a22"></div>',False)
job('ebonite',16,16,'<div style="width:16px;height:16px;background:#1d1d20"></div>',False)
# ---- табло 512×144 (слева направо для зрителя: раунд · время · жизни)
sock=''.join(f'<div style="position:absolute;left:{397+i*34}px;top:68px;width:28px;height:28px;border-radius:50%;background:#1a1410;box-shadow:inset 0 0 0 3px #3a2a1c"></div>' for i in range(3))
job('board',512,144,f'''<div style="width:512px;height:144px;background:#3a2414;padding:8px;position:relative">
<div style="width:496px;height:128px;background:#0c0606;box-shadow:inset 0 0 22px #000,inset 0 0 0 2px #1c1917;position:relative">
<div style="position:absolute;left:18px;top:12px;font-family:M;font-size:12px;letter-spacing:3px;color:#7a2a24">РАУНД</div>
<div style="position:absolute;left:206px;top:8px;font-family:M;font-size:12px;letter-spacing:3px;color:#7a2a24">ВРЕМЯ</div>
<div style="position:absolute;left:400px;top:12px;font-family:M;font-size:12px;letter-spacing:3px;color:#7a2a24">ЖИЗНИ</div>
<div style="position:absolute;left:150px;top:10px;width:2px;height:108px;background:#1f1412"></div><div style="position:absolute;left:372px;top:10px;width:2px;height:108px;background:#1f1412"></div>
</div>{sock}
<div style="position:absolute;left:3px;top:3px;width:4px;height:4px;background:#8a7a64;border-radius:50%"></div><div style="position:absolute;right:3px;top:3px;width:4px;height:4px;background:#8a7a64;border-radius:50%"></div></div>''',False)
job('bulb_on',16,16,'<div style="width:16px;height:16px;background:#ffcf6a;box-shadow:inset 0 0 0 2px #ffe9b0"></div>',False)
job('bulb_off',16,16,'<div style="width:16px;height:16px;background:#2a2018;box-shadow:inset 0 0 0 2px #4a3a2a"></div>',False)
# ---- проигрыватель «Вега-108» (верх 128×112)
job('vega_top',128,112,'''<div style="width:128px;height:112px;background:#6b4426;box-shadow:inset 0 0 0 3px #3a2414;position:relative">
<div style="position:absolute;left:10px;top:10px;width:88px;height:88px;border-radius:50%;background:#2a2622;box-shadow:inset 0 0 0 3px #1a1714"></div>
<div style="position:absolute;left:51px;top:51px;width:6px;height:6px;border-radius:50%;background:#c9c2b0"></div>
<div style="position:absolute;left:104px;top:12px;width:12px;height:12px;border-radius:50%;background:#9a9a9e;box-shadow:inset 0 0 0 3px #5c5c60"></div>
<div style="position:absolute;left:108px;top:22px;width:4px;height:62px;background:#b4b4b8;transform:rotate(18deg);transform-origin:top"></div>
<div style="position:absolute;right:6px;bottom:4px;font-family:M;font-size:6px;color:#e8d6b4">ВЕГА-108</div></div>''',False)
job('walnut',16,16,'<div style="width:16px;height:16px;background:#5a3a22;box-shadow:inset -1px 0 0 #3e2716"><div style="position:absolute;left:4px;top:0;width:1px;height:16px;background:#664429"></div><div style="position:absolute;left:10px;top:0;width:1px;height:16px;background:#4e321d"></div></div>',False)
# ---- кнопка: табличка «НАЧАТЬ ИГРУ» 64×64 (пластина 56×28 по центру)
job('button_label',64,64,'<div style="position:absolute;left:4px;top:18px;width:56px;height:28px;background:#e8dcc0;box-shadow:inset 0 0 0 1px #2a1d14;display:flex;align-items:center;justify-content:center"><div class="b" style="font-size:11px;line-height:11px;color:#2a1d14;text-align:center;letter-spacing:1px">НАЧАТЬ<br>ИГРУ</div></div>')
# ---- «ИДЁТ ЗАПИСЬ» 64×64 (табличка 60×16)
for on in (0,1):
    bg='#a3161f' if on else '#3a1210'; fg='#ffffff' if on else '#8a5a50'
    job(f'sign_{"on" if on else "off"}',64,64,f'<div style="position:absolute;left:2px;top:24px;width:60px;height:16px;background:{bg};box-shadow:inset 0 0 0 1px #1a0806;display:flex;align-items:center;justify-content:center"><div style="font-family:M;font-size:7px;color:{fg};letter-spacing:1px">ИДЁТ ЗАПИСЬ</div></div>')
# ---- гитары 128×128 (вертикально)
G=[('guitar_red','<rect x="58" y="6" width="12" height="70" fill="#7a5230"/><rect x="54" y="0" width="20" height="14" rx="3" fill="#2a1d14"/><path d="M64 70c-24 0-32 18-26 34-12 6-8 22 6 24h40c14-2 18-18 6-24 6-16-2-34-26-34z" fill="#d7263d" stroke="#5a0e16" stroke-width="3"/><rect x="52" y="100" width="24" height="5" fill="#ece6d8"/><rect x="52" y="88" width="24" height="5" fill="#ece6d8"/><path d="M62 10v100M66 10v100" stroke="#ddd" stroke-width="0.8"/><path d="M70 20q14 30 6 50" stroke="#ddd" stroke-width="0.8" fill="none"/>'),
   ('guitar_acoustic','<rect x="58" y="6" width="12" height="62" fill="#7a5230"/><rect x="54" y="0" width="20" height="14" rx="3" fill="#2a1d14"/><path d="M64 60c-20 0-28 14-22 28-18 6-18 38 4 40h36c22-2 22-34 4-40 6-14-2-28-22-28z" fill="#c98a4a" stroke="#6b4426" stroke-width="3"/><circle cx="64" cy="96" r="10" fill="#2a1d14"/><path d="M56 116h16" stroke="#3a2414" stroke-width="3"/>'),
   ('guitar_bass','<rect x="60" y="0" width="9" height="84" fill="#3a2414"/><rect x="56" y="0" width="16" height="12" rx="3" fill="#1a1210"/><path d="M64 78c-16 0-24 12-18 24l-8 8c-6 12 6 18 26 18s32-6 26-18l-6-8c4-12-4-24-20-24z" fill="#1d1d1f" stroke="#000" stroke-width="3"/><rect x="52" y="104" width="24" height="6" fill="#9a9a9e"/><path d="M36 82l12 18" stroke="#ece6d8" stroke-width="3"/>')]
for n,sv in G: job(n,128,128,f'<svg width="128" height="128" viewBox="0 0 128 128">{sv}</svg>')
# ---- плакаты альбомов 256×256 (лист 180×256)
def album(n,bg,label,lc,art,title,tc,tsize):
    job(n,256,256,f'''<div style="position:absolute;left:38px;top:0;width:180px;height:256px;background:{bg};padding:12px;display:flex;flex-direction:column;align-items:center;gap:8px">
<div style="font-family:M;font-size:8px;letter-spacing:1px;color:{lc}">{label}</div>{art}
<div class="b" style="font-size:{tsize}px;line-height:{tsize-2}px;color:{tc};text-align:center;transform:scaleX(0.88)">{title}</div></div>''')
album('album_1','#1a1210','ДЕБЮТНЫЙ АЛЬБОМ · 2019','#c9a77a','<svg width="130" height="130" viewBox="0 0 170 170"><path d="M45 40l20 22 20-30 20 30 20-22-8 46H53z" fill="#f2c230" stroke="#a7831a" stroke-width="3"/><rect x="55" y="96" width="60" height="12" fill="#ece6d8"/><path d="M50 108h70c0 22-14 34-35 34s-35-12-35-34z" fill="#ece6d8"/><rect x="70" y="140" width="30" height="20" fill="#d8d0bc"/></svg>','ВАДИМ<br>НА ТРОНЕ','#f2c230',32)
album('album_2','#3d6b3a','СИНГЛ · ХИТ (НЕТ)','#e8f0d0','<svg width="130" height="130" viewBox="0 0 170 170"><path d="M30 140c0-40 20-60 50-60l10-40c4-14 30-14 34 0l-6 20c14 4 18 20 4 26l-16 4c10 20 6 50-10 50z" fill="#6b4a2a" stroke="#2a1d14" stroke-width="3"/><circle cx="108" cy="46" r="5" fill="#fff"/><circle cx="109" cy="47" r="2" fill="#000"/></svg>','ГОВНОЗАВР<br>ТИМОФЕЙ','#f4ecd0',27)
album('album_3','#e8dcc0','EP · НА ВАШИ ДЕНЬГИ','#6b5640','<div style="width:130px;height:130px;background:#2a1d14;display:flex;align-items:center;justify-content:center"><div class="b" style="font-size:96px;color:#3fae49">₽</div></div>','БАБЛО<br>НА СТУДИЮ','#a3161f',30)
album('album_4','#0d1a3a','LIVE · feat. ГОРОД 24','#9fb4e6','<div style="width:130px;height:130px;background:#102a6b;box-shadow:inset 0 0 0 4px #d7263d;display:flex;flex-direction:column;justify-content:center;gap:8px"><div class="b" style="font-size:14px;color:#fff;text-align:center">СРОЧНЫЕ НОВОСТИ</div><div style="height:18px;background:#f2c230;font-family:M;font-size:9px;display:flex;align-items:center;justify-content:center">8 (800) 555-35-35</div></div>','НЕ ВЫХОДИТЕ<br>НОЧЬЮ ИЗ ДОМА','#f4ecd0',22)
job('platinum',256,256,'''<div style="position:absolute;left:28px;top:20px;width:200px;height:236px;background:#3a2414;box-shadow:inset 0 0 0 6px #c9a77a;display:flex;flex-direction:column;align-items:center;padding-top:20px;gap:10px">
<div style="width:130px;height:130px;border-radius:50%;background:#d8d8dc;box-shadow:inset 0 0 0 8px #bfbfc4,inset 0 0 0 22px #e8e8ec,inset 0 0 0 44px #c4c4c8;display:flex;align-items:center;justify-content:center"><div style="width:34px;height:34px;border-radius:50%;background:#a3161f"></div></div>
<div class="b" style="font-size:18px;color:#f2d9a8">ПЛАТИНОВЫЙ ДИСК</div><div style="font-family:M;font-size:11px;color:#e8d6b4;text-align:center">ПРОДАНО: 3 КОПИИ<br>(ВСЕ — МАМЕ)</div></div>''')
# ---- комбик, пульт звукорежиссёра, абажур
job('amp_front',64,64,'''<div style="width:64px;height:64px;background:#1d1d1f;padding:5px;position:relative"><div style="width:54px;height:42px;background:repeating-linear-gradient(45deg,#3a3632 0 2px,#2a2622 2px 4px);box-shadow:inset 0 0 0 2px #0a0a0a"></div>
<div class="b" style="position:absolute;left:6px;bottom:4px;font-size:8px;color:#c9a77a">ВАДИМ-АМП</div><div style="position:absolute;right:6px;bottom:6px;width:4px;height:4px;border-radius:50%;background:#d7263d"></div></div>''',False)
job('amp_side',16,16,'<div style="width:16px;height:16px;background:#1d1d1f;box-shadow:inset 0 0 0 1px #0a0a0a"></div>',False)
fad=''.join(f'<div style="position:absolute;left:{8+i*12}px;top:16px;width:2px;height:24px;background:#111"></div><div style="position:absolute;left:{5+i*12}px;top:{20+(i*7)%14}px;width:8px;height:5px;background:#d8d0bc"></div><div style="position:absolute;left:{6+i*12}px;top:5px;width:6px;height:6px;border-radius:50%;background:#555;box-shadow:inset 0 0 0 1px #888"></div>' for i in range(8))
job('mix_top',128,48,f'''<div style="width:128px;height:48px;background:#2b2b2e;box-shadow:inset 0 0 0 2px #0e0e10;position:relative">{fad}
<div style="position:absolute;left:102px;top:6px;width:20px;height:14px;background:#f2e3b8;box-shadow:inset 0 0 0 1px #3a2e22"></div>
<div style="position:absolute;left:102px;top:24px;width:4px;height:4px;background:#3fae49"></div><div style="position:absolute;left:108px;top:24px;width:4px;height:4px;background:#f2c230"></div><div style="position:absolute;left:114px;top:24px;width:4px;height:4px;background:#d7263d"></div></div>''',False)
job('shade',16,16,'<div style="width:16px;height:16px;background:#4a2e1a;box-shadow:inset 0 -2px 0 #2a1a10,inset 0 2px 0 #6b4426"></div>',False)
job('shade_glow',16,16,'<div style="width:16px;height:16px;background:#ffcf6a;box-shadow:inset 0 0 0 2px #ffb648"></div>',False)
# ---- блоки стен: поролон ромбами, орех, латунь
job('foam',16,16,'<div style="width:16px;height:16px;background:#2c2a30;position:relative"><svg width="16" height="16"><path d="M0 0L8 8L0 16ZM16 0L8 8L16 16Z" fill="#3e3b45"/><path d="M0 0L8 8L16 0Z" fill="#4a4752"/><path d="M0 16L8 8L16 16Z" fill="#26242a"/></svg></div>',False)
job('walnut_panel',16,16,'<div style="width:16px;height:16px;background:#5a3a22;box-shadow:inset -1px 0 0 #3e2716,inset 0 -1px 0 #3e2716,inset 1px 1px 0 #6b4a2e"><div style="position:absolute;left:5px;top:1px;width:1px;height:14px;background:#664429"></div><div style="position:absolute;left:11px;top:1px;width:1px;height:14px;background:#4e321d"></div></div>',False)
job('brass',16,16,'<div style="width:16px;height:16px;background:#5a3a22"><div style="position:absolute;left:0;top:5px;width:16px;height:6px;background:#c9a77a;box-shadow:inset 0 1px 0 #f2d9a8,inset 0 -1px 0 #8a6a3a"></div></div>',False)
json.dump(J,open('jobs.json','w'),ensure_ascii=False)
print(len(J))
