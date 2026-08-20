/** Android NumberPicker defaults (Material alarm clock proportions). */
export const WHEEL_ITEM_HEIGHT = 48;
export const WHEEL_HEIGHT = 180;
export const WHEEL_SNAP_MS = 300;
export const WHEEL_DIVIDER_HEIGHT = 1;

export function wheelScaleForViewport(viewportWidth: number): number {
  return Math.max(0.82, Math.min(1.2, viewportWidth / 390));
}

export function wheelMetrics(viewportWidth: number) {
  const scale = wheelScaleForViewport(viewportWidth);
  return {
    scale,
    height: Math.round(WHEEL_HEIGHT * scale),
    itemHeight: Math.round(WHEEL_ITEM_HEIGHT * scale),
    dividerHeight: Math.max(1, Math.round(WHEEL_DIVIDER_HEIGHT * scale)),
    hourWidth: Math.round(72 * scale),
    minuteWidth: Math.round(72 * scale),
    dayWidth: Math.round(64 * scale),
    monthWidth: Math.round(96 * scale),
    yearWidth: Math.round(80 * scale),
    separatorSize: Math.round(34 * scale),
  };
}

export function easeOutCubic(t: number): number {
  return 1 - (1 - t) ** 3;
}

export function animateScrollTop(element: HTMLElement, target: number, durationMs: number): Promise<void> {
  const start = element.scrollTop;
  const delta = target - start;
  if (Math.abs(delta) < 0.5) {
    element.scrollTop = target;
    return Promise.resolve();
  }
  const startTime = performance.now();
  return new Promise((resolve) => {
    const step = (now: number) => {
      const progress = Math.min(1, (now - startTime) / durationMs);
      element.scrollTop = start + delta * easeOutCubic(progress);
      if (progress < 1) {
        requestAnimationFrame(step);
      } else {
        element.scrollTop = target;
        resolve();
      }
    };
    requestAnimationFrame(step);
  });
}

export function readWheelIndex(scrollTop: number, itemHeight: number): number {
  return Math.round(scrollTop / itemHeight);
}

export function wheelIndexToScrollTop(index: number, itemHeight: number): number {
  return index * itemHeight;
}

export function clampWheelIndex(index: number, length: number): number {
  if (length <= 0) return 0;
  return Math.max(0, Math.min(index, length - 1));
}

export function wrapWheelIndex(index: number, length: number): number {
  if (length <= 0) return 0;
  return ((index % length) + length) % length;
}

export function wheelItemVisual(distanceFromCenter: number) {
  const abs = Math.min(Math.abs(distanceFromCenter), 2.5);
  return {
    opacity: Math.max(0.18, 1 - abs * 0.38),
    scale: Math.max(0.82, 1 - abs * 0.08),
  };
}
