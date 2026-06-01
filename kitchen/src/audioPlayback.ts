let audioContext: AudioContext | null = null;
let unlocked = false;
const bufferCache = new Map<string, AudioBuffer>();
let loopingSource: AudioBufferSourceNode | null = null;

function getAudioContext(): AudioContext {
  if (!audioContext) {
    audioContext = new AudioContext();
  }
  return audioContext;
}

export function isAudioUnlocked(): boolean {
  return unlocked && getAudioContext().state === "running";
}

export async function unlockAudioPlayback(): Promise<boolean> {
  const context = getAudioContext();
  if (context.state === "suspended") {
    await context.resume();
  }
  unlocked = context.state === "running";
  return unlocked;
}

export function clearSoundCache(): void {
  bufferCache.clear();
  stopLoopingSound();
}

async function loadBuffer(url: string): Promise<AudioBuffer | null> {
  const cached = bufferCache.get(url);
  if (cached) return cached;

  try {
    const response = await fetch(url);
    if (!response.ok) return null;
    const arrayBuffer = await response.arrayBuffer();
    const buffer = await getAudioContext().decodeAudioData(arrayBuffer);
    bufferCache.set(url, buffer);
    return buffer;
  } catch {
    return null;
  }
}

export async function preloadSoundUrl(url: string): Promise<boolean> {
  if (!url) return false;
  await unlockAudioPlayback();
  return (await loadBuffer(url)) !== null;
}

export async function playSoundUrl(url: string): Promise<boolean> {
  if (!url) return false;

  const context = getAudioContext();
  if (context.state === "suspended") {
    await context.resume();
  }
  if (context.state !== "running") {
    return false;
  }

  unlocked = true;
  const buffer = await loadBuffer(url);
  if (!buffer) return false;

  const source = context.createBufferSource();
  source.buffer = buffer;
  source.connect(context.destination);
  source.start(0);
  return true;
}

export function stopLoopingSound(): void {
  if (!loopingSource) return;
  try {
    loopingSource.stop();
  } catch {
    // Source may already be stopped.
  }
  loopingSource.disconnect();
  loopingSource = null;
}

export async function playLoopingSound(url: string): Promise<boolean> {
  if (!url) return false;
  stopLoopingSound();

  const context = getAudioContext();
  if (context.state === "suspended") {
    await context.resume();
  }
  if (context.state !== "running") {
    return false;
  }

  unlocked = true;
  const buffer = await loadBuffer(url);
  if (!buffer) return false;

  const source = context.createBufferSource();
  source.buffer = buffer;
  source.loop = true;
  source.connect(context.destination);
  source.start(0);
  loopingSource = source;
  return true;
}
