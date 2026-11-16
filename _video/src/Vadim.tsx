import React from 'react';
import {AbsoluteFill, Img, interpolate, staticFile, useCurrentFrame, Easing} from 'remotion';
import {loadFont as loadOswald} from '@remotion/google-fonts/Oswald';
import {loadFont as loadPTMono} from '@remotion/google-fonts/PTMono';
import ENV from './vadim_env.json';

const {fontFamily: OSWALD} = loadOswald('normal', {weights: ['500', '700'], subsets: ['latin', 'cyrillic']});
const {fontFamily: PTMONO} = loadPTMono('normal', {weights: ['400'], subsets: ['latin', 'cyrillic']});

/**
 * Мультик Вадима на мониторе комнаты 1 (этап 4б). Раскадровка — холст Claude Design
 * «Мультик Вадима — комната 1» (https://claude.ai/artifact/DixYoCzuDrqzYSbBTpiKRZ).
 * Ролик без звука: голос играет игра из монитора по тикам Room1.LINES. Время ролика = phaseTick / 20.
 * Огибающая громкости голоса (vadim_env.json, 30 к/с) двигает голову и руки.
 */
export const FPS = 30;
export const DURATION = 52;
export const W = 960, H = 600;

// тайминги реплик (с) — как в Room1.LINES (тик / 20)
const L = {r101: 1.0, r102: 7.3, r103: 15.8, r104: 24.8, r106: 31.6, rockets: 36.2, r201: 40.5, r202: 49.3, end: 52};
const CUTS = [L.r102, L.r103, L.r104, L.r106, L.rockets, L.r201, L.r202];

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const k = (t: number, a: number, b: number, e = Easing.inOut(Easing.cubic)) => interpolate(t, [a, b], [0, 1], {...clamp, easing: e});
const rnd = (n: number) => { const x = Math.sin(n * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); };
const env = (f: number) => (ENV as number[])[Math.max(0, Math.min((ENV as number[]).length - 1, Math.round(f)))] ?? 0;

// ===== позы: [плечо левой (на экране), плечо правой, наклон головы, наклон корпуса, сдвиг y] =====
type Pose = {aL: number; aR: number; head: number; lean: number; y: number; zoom: number};
const P: Record<string, Pose> = {
  idle:   {aL: 6, aR: -6, head: -2, lean: 0, y: 0, zoom: 1},
  wave:   {aL: 6, aR: -150, head: 6, lean: 2, y: 0, zoom: 1.08},
  shrug:  {aL: 70, aR: -70, head: -8, lean: 0, y: 4, zoom: 1.0},
  angry:  {aL: 30, aR: -100, head: 10, lean: -3, y: -6, zoom: 1.18},
  point:  {aL: 8, aR: -62, head: -4, lean: -2, y: 0, zoom: 1.05},
  hold:   {aL: 40, aR: -80, head: 8, lean: 0, y: 0, zoom: 1.12},
  up:     {aL: 6, aR: -168, head: -10, lean: 3, y: 6, zoom: 0.98},
  rub:    {aL: -28, aR: 28, head: 4, lean: 0, y: 2, zoom: 1.05},
  laugh:  {aL: 128, aR: -128, head: 14, lean: 0, y: -14, zoom: 1.1},
  final:  {aL: 8, aR: -70, head: -6, lean: -3, y: 0, zoom: 1.22},
};
// ключевые кадры поз (время, поза)
const KEYS: [number, keyof typeof P][] = [
  [0, 'idle'], [1.6, 'wave'], [4.2, 'wave'], [5.4, 'idle'],
  [7.6, 'shrug'], [10.6, 'shrug'], [11.4, 'angry'], [14.8, 'angry'], [15.5, 'idle'],
  [16.4, 'point'], [23.6, 'point'], [24.4, 'idle'],
  [25.4, 'hold'], [30.6, 'hold'], [31.2, 'idle'],
  [32.0, 'point'], [33.6, 'up'], [36.0, 'up'], [36.8, 'rub'], [40.2, 'rub'],
  [40.7, 'laugh'], [48.6, 'laugh'], [49.3, 'final'], [52, 'final'],
];
const pose = (t: number): Pose => {
  let i = 0;
  while (i < KEYS.length - 2 && KEYS[i + 1][0] <= t) i++;
  const [ta, pa] = KEYS[i], [tb, pb] = KEYS[i + 1];
  const e = k(t, ta, tb);
  const a = P[pa], b = P[pb];
  const m = (x: number, y: number) => x + (y - x) * e;
  return {aL: m(a.aL, b.aL), aR: m(a.aR, b.aR), head: m(a.head, b.head), lean: m(a.lean, b.lean), y: m(a.y, b.y), zoom: m(a.zoom, b.zoom)};
};

// надпись на правом ЭЛТ по времени
const caption = (t: number): string => {
  if (t < L.r102) return 'ПРИВЕТ';
  if (t < L.r103) return 'МОЯ\nМУЗЫКА';
  if (t < L.r104) return 'ВЫ\nПРИВЯЗАНЫ';
  if (t < L.r106) return 'НОЖНИЦЫ';
  if (t < L.rockets) return t < 33.4 ? '5:00' : 'НАВЕРХ';
  if (t < L.r201) return '. . .';
  if (t < L.r202) return 'ХА-ХА-ХА';
  return 'ВРЕМЯ\nПОШЛО';
};

// ===== помехи: SVG-шум с новым зерном каждый кадр =====
const Noise: React.FC<{f: number; w: number; h: number; o?: number; freq?: number; id: string}> = ({f, w, h, o = 1, freq = 0.9, id}) => (
  <svg width={w} height={h} style={{position: 'absolute', left: 0, top: 0, opacity: o}} aria-hidden>
    <filter id={id}>
      <feTurbulence type="fractalNoise" baseFrequency={freq} numOctaves={2} seed={f % 997} />
      <feColorMatrix type="saturate" values="0" />
    </filter>
    <rect width={w} height={h} filter={`url(#${id})`} />
  </svg>
);

const Crt: React.FC<{x: number; y: number; w: number; h: number; f: number; id: string; children?: React.ReactNode; dark?: boolean; o?: number}> =
  ({x, y, w, h, f, id, children, dark, o = 0.85}) => (
  <div style={{position: 'absolute', left: x, top: y, width: w, height: h, background: '#1a1a1a', borderRadius: 10, padding: 11, boxSizing: 'border-box', boxShadow: '0 6px 0 #0a0a0a'}}>
    <div style={{position: 'relative', width: '100%', height: '100%', borderRadius: 16, overflow: 'hidden', background: dark ? '#0b0b0b' : '#333'}}>
      {!dark && <Noise f={f} w={w} h={h} o={o} id={id} />}
      {children}
    </div>
  </div>
);

// ===== фон: подвал =====
const Background: React.FC<{f: number; t: number; red: number}> = ({f, t, red}) => {
  const flick = 0.85 + 0.15 * Math.sin(t * 13) * Math.sin(t * 3.1) + (rnd(Math.floor(t * 12)) < 0.04 ? -0.35 : 0);
  const cap = caption(t);
  const capBlink = t >= L.rockets && t < L.r201 ? (Math.floor(t * 2) % 2 ? 1 : 0.3) : 1;
  return (
    <AbsoluteFill style={{background: '#070303'}}>
      <AbsoluteFill style={{backgroundColor: '#1a1211', backgroundImage: 'linear-gradient(rgba(0,0,0,0.35) 2px,transparent 2px),linear-gradient(90deg,rgba(0,0,0,0.35) 2px,transparent 2px)', backgroundSize: '96px 64px'}} />
      {/* красный свет лампы */}
      <div style={{position: 'absolute', left: 230, top: -160, width: 500, height: 600, opacity: flick, background: `radial-gradient(ellipse at 50% 30%, rgba(214,24,24,${0.5 + red * 0.4}), rgba(120,8,8,0.25) 45%, transparent 70%)`}} />
      {/* пятна */}
      <div style={{position: 'absolute', left: 70, top: 120, width: 120, height: 150, borderRadius: '50%', background: 'radial-gradient(circle, rgba(70,6,6,0.9), rgba(70,6,6,0) 70%)'}} />
      <div style={{position: 'absolute', left: 810, top: 70, width: 90, height: 60, borderRadius: '50%', background: 'radial-gradient(circle, rgba(80,8,8,0.85), rgba(80,8,8,0) 70%)'}} />
      <div style={{position: 'absolute', left: 650, top: 330, width: 16, height: 120, background: 'linear-gradient(rgba(90,10,10,0.8), rgba(90,10,10,0))'}} />
      <div style={{position: 'absolute', left: 300, top: 470, width: 60, height: 40, borderRadius: '50%', background: 'radial-gradient(circle, rgba(60,4,4,0.8), rgba(60,4,4,0) 70%)'}} />
      {/* кабели */}
      <svg width={W} height={H} style={{position: 'absolute', left: 0, top: 0}} aria-hidden>
        <path d="M0 60 C 160 140, 280 20, 480 70 S 800 30, 960 110" stroke="#050505" strokeWidth={7} fill="none" />
        <path d="M0 95 C 120 170, 330 60, 470 120 S 760 70, 960 150" stroke="#140808" strokeWidth={5} fill="none" />
        <path d={`M210 0 C 220 120, ${190 + Math.sin(t * 0.8) * 4} 200, 230 320`} stroke="#060606" strokeWidth={6} fill="none" />
        <path d={`M760 0 C 740 90, ${790 + Math.sin(t * 0.6 + 1) * 4} 170, 760 260`} stroke="#0d0606" strokeWidth={5} fill="none" />
        <path d="M480 0 L 480 34" stroke="#222" strokeWidth={3} />
      </svg>
      {/* лампа */}
      <div style={{position: 'absolute', left: 466, top: 30, width: 28, height: 36, borderRadius: '50% 50% 45% 45%', background: '#ff4a3a', opacity: 0.6 + flick * 0.4, boxShadow: `0 0 ${40 + red * 30}px ${18 + red * 14}px rgba(255,40,30,${0.45 + red * 0.3})`}} />
      {/* ЭЛТ слева */}
      <Crt x={24} y={230} w={210} h={150} f={f} id="n1">
        <div style={{position: 'absolute', left: 10, bottom: 8, fontFamily: PTMONO, fontSize: 12, color: '#fff'}}>CAM 03</div>
      </Crt>
      <Crt x={24} y={390} w={120} h={100} f={f + 37} id="n2" o={0.7} />
      <div style={{position: 'absolute', left: 154, top: 390, width: 80, height: 100, background: '#121212', borderRadius: 8, padding: 9, boxSizing: 'border-box'}}>
        <div style={{width: '100%', height: '100%', borderRadius: 10, background: '#2a0606', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: PTMONO, fontSize: 11, color: '#ff4b3e', opacity: Math.floor(t * 1.5) % 2 ? 1 : 0.4}}>REC</div>
      </div>
      {/* ЭЛТ справа: надписи */}
      <Crt x={726} y={250} w={210} h={150} f={f} id="n3" dark>
        <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', textAlign: 'center', whiteSpace: 'pre-line', fontFamily: OSWALD, fontWeight: 700, fontSize: cap.length > 9 ? 24 : 30, lineHeight: 1.05, letterSpacing: 1, color: '#d7192b', opacity: capBlink, textShadow: '0 0 12px rgba(215,25,43,0.6)'}}>{cap}</div>
      </Crt>
      <Crt x={726} y={410} w={210} h={90} f={f + 71} id="n4" o={0.65} />
    </AbsoluteFill>
  );
};

// ===== Вадим (части скина Вадима, пиксели без сглаживания) =====
const PX: React.CSSProperties = {imageRendering: 'pixelated', position: 'absolute'};
const Scissors: React.FC = () => (
  <svg width={46} height={46} viewBox="0 0 46 46" style={{position: 'absolute', left: 22, top: 250}} aria-hidden>
    <circle cx={12} cy={34} r={7} fill="none" stroke="#c9c9c9" strokeWidth={4} />
    <circle cx={30} cy={38} r={6} fill="none" stroke="#c9c9c9" strokeWidth={4} />
    <path d="M16 29 L 38 4 M 27 33 L 30 6" stroke="#e6e6e6" strokeWidth={4} strokeLinecap="round" />
  </svg>
);

const Vadim: React.FC<{t: number; f: number}> = ({t, f}) => {
  const p = pose(t);
  const e = env(f);
  const laugh = t >= L.r201 && t < L.r202 - 0.6;
  const rub = t >= 36.8 && t < 40.2;
  // жесты в такт речи
  const talkL = Math.sin(t * 7.3) * 9 * e, talkR = Math.sin(t * 6.1 + 1) * 12 * e;
  const headBob = Math.sin(t * 9.0) * 4 * e + Math.sin(t * 1.3) * 1.5;
  const breathe = Math.sin(t * 1.7) * 3;
  const shakeX = laugh ? (rnd(f * 3.1) - 0.5) * 10 * (0.4 + e) : 0;
  const shakeY = laugh ? (rnd(f * 7.7) - 0.5) * 8 * (0.4 + e) : 0;
  const rubA = rub ? Math.sin(t * 14) * 10 : 0;
  const aL = p.aL + talkL + (laugh ? Math.sin(t * 15) * 10 : 0) + rubA;
  const aR = p.aR + talkR + (laugh ? Math.sin(t * 15 + 2) * 10 : 0) - rubA;
  const head = p.head + headBob + (laugh ? Math.sin(t * 21) * 6 : 0);
  const y = p.y - e * 5 + breathe * 0.3;
  const holdScissors = t >= 25.4 && t < 31.0;
  return (
    <AbsoluteFill style={{transform: `translate(${shakeX}px, ${y + shakeY}px) scale(${p.zoom}) rotate(${p.lean + Math.sin(t * 0.9) * 0.8}deg)`, transformOrigin: '480px 600px'}}>
      {/* тень за фигурой */}
      <div style={{position: 'absolute', left: 300, top: 120, width: 360, height: 520, borderRadius: '50%', background: 'radial-gradient(ellipse, rgba(0,0,0,0.55), rgba(0,0,0,0) 65%)'}} />
      <div style={{...PX, left: 296, top: 300, width: 92, height: 276, transform: `rotate(${aL}deg)`, transformOrigin: '46px 14px'}}>
        <Img src={staticFile('vadim/arm_l.png')} style={{...PX, left: 0, top: 0, width: 92, height: 276}} />
      </div>
      <div style={{...PX, left: 572, top: 300, width: 92, height: 276, transform: `rotate(${aR}deg)`, transformOrigin: '46px 14px'}}>
        <Img src={staticFile('vadim/arm_r.png')} style={{...PX, left: 0, top: 0, width: 92, height: 276}} />
        {holdScissors && <Scissors />}
      </div>
      <Img src={staticFile('vadim/body.png')} style={{...PX, left: 388, top: 300 + breathe * 0.4, width: 184, height: 276}} />
      <div style={{position: 'absolute', left: 388, top: 116 + breathe * 0.5, width: 184, height: 184, transform: `rotate(${head}deg)`, transformOrigin: '92px 184px', boxShadow: '0 0 0 4px #120b0a, 0 18px 40px rgba(0,0,0,0.6)'}}>
        <Img src={staticFile('vadim/face.png')} style={{...PX, left: 0, top: 0, width: 184, height: 184}} />
        {/* блик красной лампы сверху */}
        <div style={{position: 'absolute', inset: 0, background: 'linear-gradient(rgba(255,40,30,0.18), rgba(0,0,0,0) 45%, rgba(0,0,0,0.25))'}} />
      </div>
    </AbsoluteFill>
  );
};

// ===== поверх: глитч на склейках, REC, таймкод, развёртка, виньетка =====
const glitchAt = (t: number) => {
  let g = 0;
  for (const c of CUTS) g = Math.max(g, 1 - Math.abs(t - c) / 0.18);
  if (t < 0.5) g = Math.max(g, 1 - t / 0.5);
  if (t > L.end - 0.5) g = Math.max(g, (t - (L.end - 0.5)) / 0.5);
  return Math.max(0, g);
};

export const Vadim4b: React.FC = () => {
  const f = useCurrentFrame();
  const t = f / FPS;
  const e = env(f);
  const laugh = t >= L.r201 && t < L.r202 - 0.6;
  const red = laugh ? 0.5 + e * 0.5 : t >= L.rockets && t < L.r201 ? 0.3 : 0.1 * e;
  const g = glitchAt(t);
  const slices = g > 0.05 ? [0, 1, 2, 3, 4, 5].map((i) => ({y: rnd(f * 13 + i) * H, h: 8 + rnd(f * 5 + i) * 40, dx: (rnd(f * 11 + i) - 0.5) * 80 * g})) : [];
  const tc = `00:${String(Math.floor(t)).padStart(2, '0')}:${String(Math.floor((t % 1) * 30)).padStart(2, '0')}`;
  const black = t < 0.25 ? 1 : t > L.end - 0.25 ? 1 : 0;
  return (
    <AbsoluteFill style={{background: '#000', overflow: 'hidden'}}>
      <AbsoluteFill style={{transform: g > 0.05 ? `translateX(${(rnd(f) - 0.5) * 24 * g}px)` : undefined}}>
        <Background f={f} t={t} red={red} />
        <Vadim t={t} f={f} />
        <AbsoluteFill style={{background: 'radial-gradient(ellipse at 50% 45%, transparent 45%, rgba(0,0,0,0.85) 100%)'}} />
        <AbsoluteFill style={{background: `rgba(255,30,20,${laugh ? 0.06 + e * 0.12 : 0})`}} />
      </AbsoluteFill>
      {/* сдвиг полос на склейке + помехи */}
      {slices.map((s, i) => (
        <div key={i} style={{position: 'absolute', left: s.dx, top: s.y, width: W, height: s.h, background: `rgba(${i % 2 ? '255,40,40' : '200,220,255'},${0.18 * g})`, mixBlendMode: 'screen'}} />
      ))}
      {g > 0.05 && <Noise f={f + 500} w={W} h={H} o={0.55 * g} freq={0.75} id="ng" />}
      {/* зерно всегда */}
      <Noise f={f + 900} w={W} h={H} o={0.07} freq={1.2} id="gr" />
      <AbsoluteFill style={{backgroundImage: 'repeating-linear-gradient(0deg,rgba(0,0,0,0.28) 0 1px,transparent 1px 3px)'}} />
      <div style={{position: 'absolute', left: 28, top: 22, display: 'flex', alignItems: 'center', gap: 10, fontFamily: PTMONO, fontSize: 18, letterSpacing: 2, color: '#ff4b3e'}}>
        <div style={{width: 12, height: 12, borderRadius: '50%', background: '#ff3b2f', opacity: Math.floor(t * 1.5) % 2 ? 1 : 0.2}} />
        <span>REC</span>
      </div>
      <div style={{position: 'absolute', right: 28, top: 22, fontFamily: PTMONO, fontSize: 16, letterSpacing: 2, color: 'rgba(232,220,214,0.75)'}}>{tc}</div>
      <div style={{position: 'absolute', left: 28, bottom: 20, fontFamily: PTMONO, fontSize: 13, letterSpacing: 3, color: 'rgba(232,220,214,0.55)'}}>ВАДИМ НА ТРОНЕ · ПЛЁНКА 1</div>
      <AbsoluteFill style={{background: '#000', opacity: black}} />
    </AbsoluteFill>
  );
};
