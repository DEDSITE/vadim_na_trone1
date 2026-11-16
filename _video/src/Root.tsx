import React from 'react';
import {Composition} from 'remotion';
import {News, FPS, DURATION} from './News';
import {Vadim4b, FPS as VFPS, DURATION as VDUR, W as VW, H as VH} from './Vadim';

export const RemotionRoot: React.FC = () => (
  <>
    <Composition id="News" component={News} durationInFrames={Math.round(DURATION * FPS)} fps={FPS} width={960} height={540} />
    <Composition id="Vadim" component={Vadim4b} durationInFrames={Math.round(VDUR * VFPS)} fps={VFPS} width={VW} height={VH} />
  </>
);
