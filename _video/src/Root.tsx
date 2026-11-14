import React from 'react';
import {Composition} from 'remotion';
import {News, FPS, DURATION} from './News';

export const RemotionRoot: React.FC = () => (
  <Composition id="News" component={News} durationInFrames={Math.round(DURATION * FPS)} fps={FPS} width={960} height={540} />
);
