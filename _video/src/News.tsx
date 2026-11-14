import React from 'react';
import {AbsoluteFill, Audio, Img, interpolate, spring, staticFile, useCurrentFrame, Easing} from 'remotion';
import {loadFont as loadOswald} from '@remotion/google-fonts/Oswald';
import {loadFont as loadPTSans} from '@remotion/google-fonts/PTSans';
import {loadFont as loadPTMono} from '@remotion/google-fonts/PTMono';
import {loadFont as loadCaveat} from '@remotion/google-fonts/Caveat';

const {fontFamily: OSWALD} = loadOswald('normal', {weights: ['500', '700'], subsets: ['latin', 'cyrillic']});
const {fontFamily: PTSANS} = loadPTSans('normal', {weights: ['400', '700'], subsets: ['latin', 'cyrillic']});
const {fontFamily: PTMONO} = loadPTMono('normal', {weights: ['400'], subsets: ['latin', 'cyrillic']});
const {fontFamily: CAVEAT} = loadCaveat('normal', {weights: ['700'], subsets: ['latin', 'cyrillic']});

export const FPS = 30;
export const DURATION = 37;

// ---- таймлайн (секунды), подогнан под озвучку и звук ----
const T = {
  introEnd: 4.0,
  studio1End: 6.9,
  missingEnd: 13.6,
  studio2End: 17.4,
  mapEnd: 27.1,
  hotlineEnd: 35.0,
  shutters: [8.6, 10.2, 11.8],
  v04: {escape: 18.0, found: 20.2, repeated: 22.5, vadim: 24.7, throne: 25.96},
  v05: {start: 27.6, dontGo: 31.9},
};
const CUTS = [T.introEnd, T.studio1End, T.missingEnd, T.studio2End, T.mapEnd];

const NAVY = '#07102A', NAVY2 = '#0A1633', RED = '#D7192B', YELLOW = '#FFC531', PAPER = '#F2F4FA', SKY = '#C9D5F5';

const TICKER = [
  'Опелелёт снова замечен над ночным кварталом',
  'Синоптики: ночью будет темно',
  'Бармен Тимофей опроверг слухи о Говнозавре',
  'Лопата дежавю выставлена в баре «Трон»',
  'Курс буханки к опелелёту не изменился',
  'Все трое — постоянные посетители бара «Трон»',
  'Владельцы бара от комментариев отказались',
  'Записки Короля продолжают находить по всему городу',
  'Завод закрыт с 1998 года',
  'Хочешь сыграть в игру?',
].join('   •   ');

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const lerp = (t: number, a: number, b: number, from = 0, to = 1, ease = Easing.out(Easing.cubic)) =>
  interpolate(t, [a, b], [from, to], {...clamp, easing: ease});
const rnd = (n: number) => {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
};

// ================= общие элементы =================
const Bug: React.FC<{t: number}> = ({t}) => {
  const k = lerp(t, 1.8, 2.3);
  const blink = Math.floor(t * 1.6) % 2 === 0 ? 1 : 0.35;
  return (
    <div style={{position: 'absolute', right: 28, top: 24, display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 6, opacity: k, transform: `translateX(${(1 - k) * 40}px)`}}>
      <div style={{display: 'flex', fontFamily: OSWALD, fontWeight: 700, fontSize: 26, lineHeight: 1}}>
        <span style={{background: '#fff', color: NAVY, padding: '6px 10px'}}>ГОРОД</span>
        <span style={{background: RED, color: '#fff', padding: '6px 10px'}}>24</span>
      </div>
      <div style={{display: 'flex', alignItems: 'center', gap: 6, fontFamily: OSWALD, fontWeight: 500, fontSize: 13, letterSpacing: 2, color: '#fff'}}>
        <span style={{width: 9, height: 9, borderRadius: '50%', background: RED, opacity: blink}} />
        ПРЯМОЙ ЭФИР
      </div>
    </div>
  );
};

const Ticker: React.FC<{t: number}> = ({t}) => {
  const k = lerp(t, 1.8, 2.3);
  const mins = 47 + Math.floor(t / 15);
  const x = -((t - 1.8) * 95);
  return (
    <div style={{position: 'absolute', left: 0, bottom: 0, width: 960, height: 44, display: 'flex', transform: `translateY(${(1 - k) * 50}px)`}}>
      <div style={{background: YELLOW, color: NAVY, fontFamily: OSWALD, fontWeight: 700, fontSize: 20, display: 'flex', alignItems: 'center', padding: '0 16px', gap: 12, zIndex: 2}}>
        <span style={{fontFamily: PTMONO, fontWeight: 400, fontSize: 18}}>02:{String(mins).padStart(2, '0')}</span>
        СРОЧНО
      </div>
      <div style={{flexGrow: 1, background: PAPER, color: NAVY, fontFamily: PTSANS, fontWeight: 700, fontSize: 19, display: 'flex', alignItems: 'center', overflow: 'hidden', whiteSpace: 'nowrap', position: 'relative'}}>
        <div style={{position: 'absolute', left: 18, transform: `translateX(${x}px)`}}>{TICKER + '   •   ' + TICKER}</div>
      </div>
    </div>
  );
};

// зерно, строки развёртки, виньетка, бегущая полоса трекинга, сдвиг при склейке
const Overlay: React.FC<{t: number; frame: number}> = ({t, frame}) => {
  const band = (t * 0.23) % 1.6;
  return (
    <>
      <svg width={960} height={540} style={{position: 'absolute', inset: 0, opacity: 0.13, mixBlendMode: 'overlay'}}>
        <filter id="grain">
          <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves={2} seed={frame % 24} />
          <feColorMatrix type="saturate" values="0" />
        </filter>
        <rect width="960" height="540" filter="url(#grain)" />
      </svg>
      <div style={{position: 'absolute', inset: 0, background: 'repeating-linear-gradient(rgba(0,0,0,.22) 0 1px, transparent 1px 3px)'}} />
      {band < 1 && (
        <div style={{position: 'absolute', left: 0, width: 960, top: band * 600 - 40, height: 26, background: 'linear-gradient(transparent, rgba(255,255,255,0.06), transparent)'}} />
      )}
      <div style={{position: 'absolute', inset: 0, background: 'radial-gradient(ellipse at center, transparent 55%, rgba(0,0,0,.6) 100%)'}} />
    </>
  );
};

// помехи-склейка: сила 1→0 за 0.3 с после каждого «вжуха»
const glitchAmount = (t: number) => {
  let g = 0;
  for (const c of CUTS) if (t >= c - 0.08 && t < c + 0.3) g = Math.max(g, 1 - Math.abs(t - c) / 0.3);
  if (t < 0.35) g = Math.max(g, 1 - t / 0.35);
  return g;
};

const GlitchBars: React.FC<{g: number; frame: number}> = ({g, frame}) => {
  if (g <= 0.01) return null;
  const bars = Array.from({length: 7}, (_, i) => {
    const y = rnd(frame * 7 + i) * 540;
    const h = 4 + rnd(frame * 13 + i) * 30;
    const dx = (rnd(frame * 3 + i) - 0.5) * 120 * g;
    return <div key={i} style={{position: 'absolute', left: dx, top: y, width: 960, height: h, background: i % 2 ? `rgba(255,255,255,${0.25 * g})` : `rgba(215,25,43,${0.3 * g})`}} />;
  });
  return <>{bars}</>;
};

const Noise: React.FC<{frame: number; opacity: number}> = ({frame, opacity}) => (
  <svg width={960} height={540} style={{position: 'absolute', inset: 0, opacity}}>
    <filter id="tvnoise">
      <feTurbulence type="fractalNoise" baseFrequency="1.4 0.8" numOctaves={1} seed={frame} />
      <feColorMatrix type="saturate" values="0" />
      <feComponentTransfer>
        <feFuncR type="linear" slope="1.6" intercept="-0.3" />
        <feFuncG type="linear" slope="1.6" intercept="-0.3" />
        <feFuncB type="linear" slope="1.6" intercept="-0.3" />
      </feComponentTransfer>
    </filter>
    <rect width="960" height="540" filter="url(#tvnoise)" />
  </svg>
);

// ================= сцены =================
const Intro: React.FC<{t: number}> = ({t}) => {
  const band = spring({frame: (t - 0.9) * FPS, fps: FPS, config: {damping: 14}});
  const title = spring({frame: (t - 1.3) * FPS, fps: FPS, config: {damping: 11, mass: 0.7}});
  const sub = lerp(t, 1.6, 2.1);
  const rot = t * 0.6;
  const shake = t > 1.3 && t < 1.55 ? (rnd(t * 99) - 0.5) * 10 : 0;
  return (
    <AbsoluteFill style={{background: NAVY, transform: `translate(${shake}px, ${shake * 0.6}px)`}}>
      <svg width={960} height={540} style={{position: 'absolute', inset: 0}}>
        <g fill="none" stroke="#1B3170" strokeWidth={1.5}>
          {[120, 190, 270, 360].map((r) => <circle key={r} cx={480} cy={250} r={r * (0.9 + 0.1 * lerp(t, 0, 1.2))} />)}
          {[0, 1, 2, 3].map((i) => <ellipse key={i} cx={480} cy={250} rx={Math.abs(Math.cos(rot + i * 0.8)) * 360} ry={360} />)}
          <line x1={0} y1={250} x2={960} y2={250} />
          <line x1={480} y1={0} x2={480} y2={540} />
        </g>
      </svg>
      <div style={{position: 'absolute', left: -60, top: 212, width: 1080, height: 116, background: RED, transform: `rotate(-4deg) translateX(${(1 - band) * -1100}px)`}} />
      <div style={{position: 'absolute', left: -60, top: 332, width: 1080, height: 10, background: YELLOW, transform: `rotate(-4deg) translateX(${(1 - band) * 1100}px)`}} />
      <div style={{position: 'absolute', left: 0, top: 150, width: 960, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 18}}>
        <div style={{display: 'flex', alignItems: 'center', gap: 10, fontFamily: OSWALD, fontWeight: 500, fontSize: 22, letterSpacing: 6, color: SKY, opacity: sub}}>
          <span style={{width: 40 * sub, height: 2, background: SKY}} />ЭКСТРЕННЫЙ ВЫПУСК<span style={{width: 40 * sub, height: 2, background: SKY}} />
        </div>
        <div style={{fontFamily: OSWALD, fontWeight: 700, fontSize: 104, lineHeight: 1, letterSpacing: 2, color: '#fff', textShadow: '4px 4px 0 #7A0712', opacity: title, transform: `rotate(-4deg) scale(${1.7 - 0.7 * title})`}}>
          СРОЧНЫЕ НОВОСТИ
        </div>
      </div>
    </AbsoluteFill>
  );
};

const Skyline: React.FC<{t: number}> = ({t}) => {
  const xs = [[0, 250, 120], [110, 190, 90], [195, 280, 140], [330, 150, 70], [395, 230, 160], [550, 170, 100], [645, 260, 130], [770, 200, 80], [845, 240, 115], [960, 220, 120]];
  const wins: React.ReactNode[] = [];
  xs.forEach(([x, y, w], bi) => {
    for (let r = 0; r < 8; r++) for (let c = 0; c < Math.floor(w / 26); c++) {
      const id = bi * 100 + r * 10 + c;
      if (rnd(id) > 0.32) continue;
      const on = rnd(id + Math.floor(t * 0.7 + rnd(id) * 5) * 0.01) > 0.08;
      wins.push(<rect key={id} x={x + 12 + c * 26} y={y + 22 + r * 34} width={10} height={12} fill={rnd(id * 3) > 0.8 ? '#8FB7FF' : '#E9B44C'} opacity={on ? 0.75 : 0.1} />);
    }
  });
  return (
    <svg width={1100} height={540} style={{position: 'absolute', left: -t * 6, top: 0}}>
      <rect width={1100} height={540} fill={NAVY2} />
      <g fill="#050B1C">{xs.map(([x, y, w], i) => <rect key={i} x={x} y={y} width={w} height={540 - y} />)}</g>
      {wins}
    </svg>
  );
};

const Studio: React.FC<{t: number; start: number; head: string; sub: string; tag: string}> = ({t, start, head, sub, tag}) => {
  const lt = t - start;
  const a = lerp(lt, 0.15, 0.45), b = lerp(lt, 0.3, 0.75), c = lerp(lt, 0.7, 1.1);
  return (
    <AbsoluteFill>
      <Skyline t={t} />
      <div style={{position: 'absolute', left: 40, top: 32, display: 'flex', alignItems: 'center', gap: 8, fontFamily: OSWALD, fontWeight: 500, fontSize: 15, letterSpacing: 3, color: SKY, opacity: lerp(lt, 0, 0.4)}}>
        <span style={{width: 9, height: 9, borderRadius: '50%', background: RED}} />{tag}
      </div>
      <div style={{position: 'absolute', left: 40, bottom: 76, width: 780, display: 'flex', flexDirection: 'column'}}>
        <div style={{display: 'flex', alignItems: 'stretch'}}>
          <div style={{background: RED, fontFamily: OSWALD, fontWeight: 700, fontSize: 22, color: '#fff', display: 'flex', alignItems: 'center', padding: '0 16px', letterSpacing: 1, transform: `scaleX(${a})`, transformOrigin: 'left'}}>СРОЧНО</div>
          <div style={{background: '#fff', color: NAVY, fontFamily: OSWALD, fontWeight: 700, fontSize: 40, lineHeight: 1, padding: '12px 20px', flexGrow: 1, clipPath: `inset(0 ${100 - b * 100}% 0 0)`, whiteSpace: 'nowrap'}}>{head}</div>
        </div>
        <div style={{background: '#12235A', color: '#E6ECFF', fontFamily: PTSANS, fontSize: 21, padding: '10px 20px', marginLeft: 96, borderBottom: `3px solid ${YELLOW}`, opacity: c, transform: `translateY(${(1 - c) * -14}px)`}}>{sub}</div>
      </div>
    </AbsoluteFill>
  );
};

const DOODLES: React.ReactNode[] = [
  // Егор: очки маркером
  <g key="1" fill="none" stroke="#111" strokeWidth={7} strokeLinecap="round">
    <circle cx={222} cy={218} r={46} pathLength={1} />
    <circle cx={338} cy={218} r={46} pathLength={1} />
    <path d="M268 214 Q280 202 292 214" pathLength={1} />
    <path d="M176 210 L140 196" pathLength={1} />
    <path d="M384 210 L418 198" pathLength={1} />
  </g>,
  // Илюха: надпись
  null,
  // Андрей: усы
  <g key="3" fill="none" stroke="#111" strokeLinecap="round">
    <path d="M228 282 C210 266 186 268 172 284 C164 294 168 306 180 304" strokeWidth={9} pathLength={1} />
    <path d="M228 282 C246 266 270 268 284 284 C292 294 288 306 276 304" strokeWidth={9} pathLength={1} />
    <path d="M196 278 L260 278" strokeWidth={12} pathLength={1} />
  </g>,
];
const PEOPLE = [
  {name: 'ЕГОР ПРОГЛОТОВ', seen: 'Последний раз видели: 3 недели назад', rot: -2},
  {name: 'ИЛЮХА ЖОПОВ', seen: 'Последний раз видели: 2 недели назад', rot: 1.5},
  {name: 'АНДРЕЙ ПИЗДАХУЕВ', seen: 'Последний раз видели: 4 дня назад', rot: -1},
];

const Missing: React.FC<{t: number}> = ({t}) => {
  const lt = t - T.studio1End;
  const push = 1 + lerp(t, T.studio1End, T.missingEnd, 0, 0.05, Easing.linear);
  const head = lerp(lt, 0.1, 0.4);
  return (
    <AbsoluteFill style={{background: NAVY2}}>
      <div style={{position: 'absolute', inset: 0, transform: `scale(${push})`}}>
        <div style={{position: 'absolute', left: 40, top: 34, display: 'flex', alignItems: 'center', gap: 14}}>
          <div style={{background: RED, color: '#fff', fontFamily: OSWALD, fontWeight: 700, fontSize: 30, lineHeight: 1, padding: '8px 16px', letterSpacing: 2, clipPath: `inset(0 ${100 - head * 100}% 0 0)`}}>РАЗЫСКИВАЮТСЯ</div>
          <div style={{fontFamily: OSWALD, fontWeight: 500, fontSize: 16, letterSpacing: 2, color: SKY, opacity: lerp(lt, 0.4, 0.8)}}>ВИДЕЛИ НОЧЬЮ, ПОСЛЕ БАРА</div>
        </div>
        <div style={{position: 'absolute', left: 40, top: 104, width: 880, display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 28}}>
          {PEOPLE.map((p, i) => {
            const at = T.shutters[i];
            const s = spring({frame: (t - at) * FPS, fps: FPS, config: {damping: 13}});
            const draw = lerp(t, at + 0.5, at + 1.3, 0, 1, Easing.inOut(Easing.quad));
            const stamp = spring({frame: (t - at - 0.35) * FPS, fps: FPS, config: {damping: 9}});
            return (
              <div key={i} style={{display: 'flex', flexDirection: 'column', background: PAPER, color: NAVY, padding: '10px 10px 12px', opacity: t >= at ? 1 : 0, transform: `translateY(${(1 - s) * -60}px) rotate(${p.rot + (1 - s) * 8}deg)`, boxShadow: '0 10px 24px rgba(0,0,0,.45)'}}>
                <div style={{height: 230, position: 'relative', overflow: 'hidden', background: '#9AA3B8'}}>
                  <Img src={staticFile(`p${i + 1}.jpg`)} style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover'}} />
                  <svg viewBox="0 0 520 470" preserveAspectRatio="none" style={{position: 'absolute', inset: 0, width: '100%', height: '100%'}}>
                    {i === 1 ? (
                      <text x={226} y={168} textAnchor="middle" transform="rotate(-6 226 160)" style={{fontFamily: CAVEAT, fontWeight: 700, fontSize: 58, fill: '#111', clipPath: `inset(0 ${100 - draw * 100}% 0 0)`}}>АНУС</text>
                    ) : (
                      <g style={{strokeDasharray: 1, strokeDashoffset: 1 - draw} as React.CSSProperties}>{DOODLES[i]}</g>
                    )}
                  </svg>
                  <div style={{position: 'absolute', right: 10, bottom: 12, border: `3px solid ${RED}`, color: RED, fontFamily: OSWALD, fontWeight: 700, fontSize: 22, padding: '2px 10px', transform: `rotate(-10deg) scale(${2.2 - 1.2 * stamp})`, opacity: stamp > 0.02 ? 1 : 0}}>ПРОПАЛ</div>
                </div>
                <div style={{fontFamily: OSWALD, fontWeight: 700, fontSize: 24, marginTop: 10, whiteSpace: 'nowrap'}}>{p.name}</div>
                <div style={{fontFamily: PTSANS, fontSize: 15, color: '#3A4460'}}>{p.seen}</div>
              </div>
            );
          })}
        </div>
      </div>
      {T.shutters.map((s) => (t >= s && t < s + 0.12 ? <AbsoluteFill key={s} style={{background: '#fff', opacity: 0.7 * (1 - (t - s) / 0.12)}} /> : null))}
    </AbsoluteFill>
  );
};

const ROUTE: [number, number][] = [[135, 125], [135, 225], [345, 230], [350, 320], [430, 340]];
const partial = (k: number) => {
  const segs = ROUTE.slice(1).map((p, i) => Math.hypot(p[0] - ROUTE[i][0], p[1] - ROUTE[i][1]));
  const total = segs.reduce((a, b) => a + b, 0);
  let left = total * k;
  const pts = [ROUTE[0]];
  for (let i = 0; i < segs.length; i++) {
    if (left >= segs[i]) { pts.push(ROUTE[i + 1]); left -= segs[i]; continue; }
    const f = left / segs[i];
    pts.push([ROUTE[i][0] + (ROUTE[i + 1][0] - ROUTE[i][0]) * f, ROUTE[i][1] + (ROUTE[i + 1][1] - ROUTE[i][1]) * f]);
    break;
  }
  return pts.map((p) => p.join(',')).join(' ');
};

const MapScene: React.FC<{t: number}> = ({t}) => {
  const v = T.v04;
  const route = lerp(t, v.escape + 0.2, v.found + 0.6, 0, 1, Easing.inOut(Easing.quad));
  const zoom = 1 + lerp(t, v.found, T.mapEnd, 0, 0.35, Easing.inOut(Easing.quad));
  const pulse = (t * 1.3) % 1;
  const appear = (at: number) => lerp(t, at, at + 0.35);
  const q1 = appear(v.vadim), q2 = appear(v.throne);
  const factoryOn = t > v.found;
  return (
    <AbsoluteFill style={{background: NAVY2}}>
      <div style={{position: 'absolute', left: 0, top: 0, width: 580, height: 496, overflow: 'hidden'}}>
        <div style={{position: 'absolute', inset: 0, transform: `scale(${zoom})`, transformOrigin: '465px 372px'}}>
          <svg width={580} height={496} viewBox="0 0 580 496">
            <rect width={580} height={496} fill="#0E1D44" />
            <g stroke="#1E3576" strokeWidth={14} fill="none" strokeLinecap="square">
              <line x1={0} y1={120} x2={580} y2={140} /><line x1={0} y1={330} x2={580} y2={300} />
              <line x1={140} y1={0} x2={120} y2={496} /><line x1={330} y1={0} x2={360} y2={496} />
            </g>
            <g stroke="#172B60" strokeWidth={5} fill="none">
              <line x1={0} y1={220} x2={580} y2={225} /><line x1={0} y1={420} x2={580} y2={410} />
              <line x1={240} y1={0} x2={235} y2={496} /><line x1={460} y1={0} x2={480} y2={496} />
            </g>
            <path d="M60 470 Q 180 420 300 450 T 580 430" stroke="#0B2A55" strokeWidth={30} fill="none" />
            <g fill="#13265A">
              <rect x={160} y={150} width={60} height={55} /><rect x={160} y={240} width={60} height={70} />
              <rect x={20} y={150} width={90} height={55} /><rect x={20} y={240} width={90} height={70} />
              <rect x={260} y={20} width={55} height={85} /><rect x={20} y={20} width={100} height={85} />
            </g>
            <rect x={390} y={330} width={150} height={90} fill="#2A1520" stroke={RED} strokeWidth={2} strokeDasharray="6 4" opacity={factoryOn ? 1 : 0.4} />
            <polyline points={partial(route)} stroke={YELLOW} strokeWidth={3} fill="none" strokeDasharray="8 6" />
            <circle cx={135} cy={125} r={9} fill={YELLOW} />
            <circle cx={465} cy={372} r={14 + pulse * 30} fill="none" stroke={RED} strokeWidth={3} opacity={(1 - pulse) * 0.8} />
            <circle cx={465} cy={372} r={14} fill={RED} />
          </svg>
          <div style={{position: 'absolute', left: 152, top: 100, background: YELLOW, color: NAVY, fontFamily: OSWALD, fontWeight: 700, fontSize: 15, padding: '3px 8px'}}>БАР «ТРОН»</div>
          <div style={{position: 'absolute', left: 378, top: 428, background: RED, color: '#fff', fontFamily: OSWALD, fontWeight: 700, fontSize: 17, padding: '4px 10px', opacity: lerp(t, v.found, v.found + 0.3)}}>ЗАБРОШЕННЫЙ ЗАВОД</div>
        </div>
      </div>
      <div style={{position: 'absolute', left: 610, top: 96, width: 320, display: 'flex', flexDirection: 'column', gap: 18}}>
        <div style={{background: RED, color: '#fff', fontFamily: OSWALD, fontWeight: 700, fontSize: 30, lineHeight: 1.1, padding: '10px 14px', clipPath: `inset(0 ${100 - appear(v.escape) * 100}% 0 0)`}}>ОДНОМУ УДАЛОСЬ ВЫБРАТЬСЯ</div>
        <div style={{fontFamily: PTSANS, fontSize: 18, color: SKY, lineHeight: 1.35, opacity: appear(v.found)}}>
          Его нашли у заброшенного завода.{' '}
          <span style={{opacity: appear(v.repeated)}}>Он повторял только одно:</span>
        </div>
        <div style={{fontFamily: OSWALD, fontWeight: 700, fontSize: 46, lineHeight: 1.05, color: '#fff', textShadow: q2 > 0 ? `${(rnd(t * 40) - 0.5) * 4}px 0 0 rgba(215,25,43,.8)` : 'none'}}>
          <span style={{opacity: q1}}>«Вадим…</span> <span style={{opacity: q2}}>на&nbsp;троне»</span>
        </div>
      </div>
    </AbsoluteFill>
  );
};

const Hotline: React.FC<{t: number}> = ({t}) => {
  const v = T.v05;
  const a = lerp(t, v.start - 0.2, v.start + 0.3), b = spring({frame: (t - v.start - 0.2) * FPS, fps: FPS, config: {damping: 12}});
  const stamp = spring({frame: (t - v.dontGo) * FPS, fps: FPS, config: {damping: 8, mass: 0.6}});
  const shake = t > v.dontGo && t < v.dontGo + 0.25 ? (rnd(t * 77) - 0.5) * 12 : 0;
  const flick = t > v.dontGo + 0.8 && Math.floor(t * 4) % 6 === 0 ? 0.55 : 1;
  return (
    <AbsoluteFill style={{background: NAVY, transform: `translate(${shake}px, ${shake * 0.5}px)`}}>
      <div style={{position: 'absolute', left: 0, top: 0, width: 960, height: 496, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 22}}>
        <div style={{fontFamily: OSWALD, fontWeight: 500, fontSize: 22, letterSpacing: 4, color: SKY, opacity: a}}>ЕСЛИ ВЫ ВИДЕЛИ ЭТИХ ЛЮДЕЙ — ЗВОНИТЕ</div>
        <div style={{display: 'flex', alignItems: 'center', gap: 18, background: '#fff', color: NAVY, padding: '14px 30px', transform: `scale(${0.6 + 0.4 * b})`, opacity: b}}>
          <svg width={46} height={46} viewBox="0 0 24 24" fill="none" stroke={RED} strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" style={{transform: `rotate(${Math.sin(t * 18) * (t < v.start + 2 ? 12 : 0)}deg)`}}>
            <path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.4 1.8.7 2.7a2 2 0 0 1-.5 2.1L8 9.8a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.5c.9.3 1.8.6 2.7.7a2 2 0 0 1 1.7 2z" />
          </svg>
          <div style={{fontFamily: OSWALD, fontWeight: 700, fontSize: 72, lineHeight: 1, letterSpacing: 2}}>8 (800) 555-35-35</div>
        </div>
        <div style={{fontFamily: PTSANS, fontSize: 17, color: '#8E9CC4', opacity: lerp(t, v.start + 0.8, v.start + 1.2)}}>Звонок бесплатный. Анонимность гарантирована</div>
        <div style={{marginTop: 18, background: RED, color: '#fff', fontFamily: OSWALD, fontWeight: 700, fontSize: 44, lineHeight: 1, padding: '12px 26px', transform: `rotate(-2deg) scale(${2.4 - 1.4 * stamp})`, opacity: t >= v.dontGo ? flick : 0}}>
          НЕ ВЫХОДИТЕ НОЧЬЮ ИЗ ДОМА
        </div>
      </div>
    </AbsoluteFill>
  );
};

// ================= сборка =================
export const News: React.FC = () => {
  const frame = useCurrentFrame();
  const t = frame / FPS;
  const g = glitchAmount(t);
  const inStatic = t >= T.hotlineEnd;

  let scene: React.ReactNode;
  if (t < T.introEnd) scene = <Intro t={t} />;
  else if (t < T.studio1End) scene = <Studio t={t} start={T.introEnd} head="В ГОРОДЕ ПРОПАДАЮТ ЛЮДИ" sub="Срочный выпуск: третье исчезновение за месяц" tag="НОЧНОЙ КВАРТАЛ · СЕЙЧАС" />;
  else if (t < T.missingEnd) scene = <Missing t={t} />;
  else if (t < T.studio2End) scene = <Studio t={t} start={T.missingEnd} head="ПОЛИЦИЯ: СВЯЗИ НЕТ" sub="Следователи не видят связи между исчезновениями" tag="ГУВД · КОММЕНТАРИЙ" />;
  else if (t < T.mapEnd) scene = <MapScene t={t} />;
  else scene = <Hotline t={t} />;

  const staticK = inStatic ? lerp(t, T.hotlineEnd, T.hotlineEnd + 0.15) : 0;
  const black = lerp(t, DURATION - 0.35, DURATION - 0.05);

  return (
    <AbsoluteFill style={{background: '#000', overflow: 'hidden'}}>
      <Audio src={staticFile('news_audio.wav')} />
      <AbsoluteFill style={{transform: `translateX(${g * (rnd(frame) - 0.5) * 40}px)`, filter: g > 0.05 ? `hue-rotate(${g * 40}deg) saturate(${1 + g})` : undefined}}>
        {scene}
        <Bug t={t} />
        <Ticker t={t} />
      </AbsoluteFill>
      <GlitchBars g={g} frame={frame} />
      {t < 0.35 && <Noise frame={frame} opacity={1 - t / 0.35} />}
      {staticK > 0 && (
        <>
          <Noise frame={frame} opacity={staticK} />
          <div style={{position: 'absolute', left: 0, width: 960, top: ((t * 900) % 700) - 80, height: 60, background: 'rgba(255,255,255,0.12)'}} />
        </>
      )}
      <Overlay t={t} frame={frame} />
      <AbsoluteFill style={{background: '#000', opacity: black}} />
    </AbsoluteFill>
  );
};
