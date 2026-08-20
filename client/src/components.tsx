import { useCallback, useEffect, useRef, useState, type ReactNode, type TouchEvent } from "react";
import {
  WHEEL_SNAP_MS,
  animateScrollTop,
  clampWheelIndex,
  readWheelIndex,
  wheelIndexToScrollTop,
  wheelItemVisual,
  wrapWheelIndex,
} from "./wheelPicker";

export type TopIcon = "home" | "profile" | "cart";

type TopZoneProps = {
  left: TopIcon;
  right: TopIcon;
  onLeft: () => void;
  onRight: () => void;
  topHeight: number;
  cartCount?: number;
};

function TopIconButton({
  icon,
  size,
  onClick,
  cartCount = 0,
}: {
  icon: TopIcon;
  size: number;
  onClick: () => void;
  cartCount?: number;
}) {
  const showBadge = icon === "cart" && cartCount > 0;
  const badgeLabel = cartCount > 99 ? "99+" : String(cartCount);
  const badgeSize = Math.max(16, size * 0.34);
  const badgeFontSize = Math.max(10, size * 0.2);

  return (
    <button type="button" className="icon-btn" onClick={onClick} aria-label={iconAlt[icon]}>
      <img src={iconSrc[icon]} alt="" style={{ width: size, height: size }} />
      {showBadge && (
        <span
          className="cart-badge"
          aria-hidden="true"
          style={{
            minWidth: badgeSize,
            height: badgeSize,
            fontSize: badgeFontSize,
            lineHeight: `${badgeSize}px`,
          }}
        >
          {badgeLabel}
        </span>
      )}
    </button>
  );
}

const iconSrc: Record<TopIcon, string> = {
  home: "/icons/home.png",
  profile: "/icons/ic_profile.png",
  cart: "/icons/ic_cart.png",
};

const iconAlt: Record<TopIcon, string> = {
  home: "Главное меню",
  profile: "Личный кабинет",
  cart: "Корзина",
};

export function TopZone({ left, right, onLeft, onRight, topHeight, cartCount = 0 }: TopZoneProps) {
  const iconSize = topHeight * 0.8;
  const padding = topHeight * 0.1;
  return (
    <header className="top-zone" style={{ height: topHeight, padding }}>
      <TopIconButton icon={left} size={iconSize} onClick={onLeft} cartCount={cartCount} />
      <TopIconButton icon={right} size={iconSize} onClick={onRight} cartCount={cartCount} />
    </header>
  );
}

export function ScreenLayout({
  topHeight,
  left,
  right,
  onLeft,
  onRight,
  cartCount,
  children,
}: TopZoneProps & { children: ReactNode }) {
  return (
    <div className="screen">
      <TopZone
        left={left}
        right={right}
        onLeft={onLeft}
        onRight={onRight}
        topHeight={topHeight}
        cartCount={cartCount}
      />
      <div className="content-zone">{children}</div>
    </div>
  );
}

export function MenuButton({
  text,
  width,
  height,
  fontSize,
  onClick,
  enabled = true,
  onOverflow,
}: {
  text: string;
  width: number;
  height: number;
  fontSize: number;
  onClick: () => void;
  enabled?: boolean;
  onOverflow?: () => void;
}) {
  return (
    <button
      type="button"
      className="menu-btn"
      style={{ width, height, fontSize }}
      onClick={onClick}
      disabled={!enabled}
      ref={(node) => {
        if (!node || !onOverflow) return;
        if (node.scrollWidth > node.clientWidth) onOverflow();
      }}
    >
      {text.toUpperCase()}
    </button>
  );
}

export function WheelPicker({
  values,
  selectedIndex,
  width,
  height,
  itemHeight,
  onChange,
  cyclic = false,
}: {
  values: string[];
  selectedIndex: number;
  width: number;
  height: number;
  itemHeight: number;
  onChange: (index: number) => void;
  cyclic?: boolean;
}) {
  const baseLength = values.length;
  const repeatCount = cyclic && baseLength > 0 ? 51 : 1;
  const middleRepeat = Math.floor(repeatCount / 2);
  const displayedValues =
    cyclic && baseLength > 0 ? Array.from({ length: repeatCount }, () => values).flat() : values;
  const displayedLength = displayedValues.length;

  const toDisplayedIndex = useCallback(
    (valueIndex: number) => middleRepeat * baseLength + wrapWheelIndex(valueIndex, baseLength),
    [baseLength, middleRepeat],
  );

  const toValueIndex = useCallback(
    (displayIndex: number) => (cyclic && baseLength > 0 ? wrapWheelIndex(displayIndex, baseLength) : clampWheelIndex(displayIndex, baseLength)),
    [baseLength, cyclic],
  );

  const listRef = useRef<HTMLDivElement>(null);
  const animatingRef = useRef(false);
  const userScrollingRef = useRef(false);
  const lastEmittedIndexRef = useRef(wrapWheelIndex(selectedIndex, baseLength));
  const touchSamplesRef = useRef<{ y: number; t: number }[]>([]);
  const rafRef = useRef<number | null>(null);
  const safeDisplayedIndex = cyclic
    ? toDisplayedIndex(selectedIndex)
    : clampWheelIndex(selectedIndex, displayedLength);
  const [visuals, setVisuals] = useState<{ opacity: number; scale: number }[]>(() =>
    displayedValues.map((_, index) => wheelItemVisual(index - safeDisplayedIndex)),
  );
  const padY = Math.max(0, (height - itemHeight) / 2);

  const updateVisuals = useCallback(
    (scrollTop: number) => {
      const centerIndex = scrollTop / itemHeight;
      setVisuals(displayedValues.map((_, index) => wheelItemVisual(index - centerIndex)));
    },
    [displayedValues, itemHeight],
  );

  const emitSelection = useCallback(
    (valueIndex: number) => {
      const normalized = wrapWheelIndex(valueIndex, baseLength);
      if (normalized === lastEmittedIndexRef.current) return;
      lastEmittedIndexRef.current = normalized;
      onChange(normalized);
    },
    [baseLength, onChange],
  );

  const snapToIndex = useCallback(
    async (displayIndex: number, animate = true) => {
      const node = listRef.current;
      if (!node || displayedLength === 0) return wrapWheelIndex(selectedIndex, baseLength);
      const valueIndex = toValueIndex(displayIndex);
      const targetDisplayIndex = cyclic ? toDisplayedIndex(valueIndex) : clampWheelIndex(displayIndex, displayedLength);
      const target = wheelIndexToScrollTop(targetDisplayIndex, itemHeight);
      animatingRef.current = true;
      userScrollingRef.current = false;
      if (animate) {
        await animateScrollTop(node, target, WHEEL_SNAP_MS);
      } else {
        node.scrollTop = target;
      }
      updateVisuals(target);
      animatingRef.current = false;
      emitSelection(valueIndex);
      return valueIndex;
    },
    [cyclic, displayedLength, emitSelection, itemHeight, selectedIndex, toDisplayedIndex, toValueIndex, updateVisuals, baseLength],
  );

  const recenterScrollIfNeeded = useCallback(() => {
    const node = listRef.current;
    if (!node || !cyclic || baseLength === 0) return;
    const index = readWheelIndex(node.scrollTop, itemHeight);
    const lowerBound = baseLength * 2;
    const upperBound = displayedLength - baseLength * 3;
    if (index < lowerBound || index > upperBound) {
      const valueIndex = wrapWheelIndex(index, baseLength);
      const centeredIndex = toDisplayedIndex(valueIndex);
      node.scrollTop = wheelIndexToScrollTop(centeredIndex, itemHeight);
      updateVisuals(node.scrollTop);
    }
  }, [baseLength, cyclic, displayedLength, itemHeight, toDisplayedIndex, updateVisuals]);

  useEffect(() => {
    if (userScrollingRef.current || animatingRef.current) return;
    const node = listRef.current;
    if (!node) return;
    const target = wheelIndexToScrollTop(safeDisplayedIndex, itemHeight);
    if (Math.abs(node.scrollTop - target) > 1) {
      node.scrollTop = target;
    }
    lastEmittedIndexRef.current = wrapWheelIndex(selectedIndex, baseLength);
    updateVisuals(target);
  }, [baseLength, itemHeight, safeDisplayedIndex, selectedIndex, updateVisuals, displayedValues.join("\u0001")]);

  const settleScroll = useCallback(() => {
    const node = listRef.current;
    if (!node || animatingRef.current) return;
    recenterScrollIfNeeded();
    const index = readWheelIndex(node.scrollTop, itemHeight);
    void snapToIndex(index, true);
  }, [itemHeight, recenterScrollIfNeeded, snapToIndex]);

  const handleScroll = () => {
    const node = listRef.current;
    if (!node || animatingRef.current) return;
    userScrollingRef.current = true;
    if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    rafRef.current = requestAnimationFrame(() => {
      updateVisuals(node.scrollTop);
      rafRef.current = null;
    });
  };

  const handleTouchStart = (event: TouchEvent<HTMLDivElement>) => {
    animatingRef.current = false;
    touchSamplesRef.current = [{ y: event.touches[0].clientY, t: performance.now() }];
  };

  const handleTouchMove = (event: TouchEvent<HTMLDivElement>) => {
    touchSamplesRef.current.push({ y: event.touches[0].clientY, t: performance.now() });
    if (touchSamplesRef.current.length > 6) touchSamplesRef.current.shift();
  };

  const handleTouchEnd = () => {
    const node = listRef.current;
    if (!node || displayedLength === 0) return;
    const samples = touchSamplesRef.current;
    touchSamplesRef.current = [];
    if (samples.length >= 2) {
      const first = samples[0];
      const last = samples[samples.length - 1];
      const dt = Math.max(1, last.t - first.t);
      const velocity = ((first.y - last.y) / dt) * 16;
      const projected = node.scrollTop + velocity * 12;
      const index = readWheelIndex(projected, itemHeight);
      void snapToIndex(index, true);
      return;
    }
    settleScroll();
  };

  useEffect(() => {
    const node = listRef.current;
    if (!node) return;
    const onScrollEnd = () => {
      if (!userScrollingRef.current) return;
      userScrollingRef.current = false;
      settleScroll();
    };
    node.addEventListener("scrollend", onScrollEnd);
    return () => {
      node.removeEventListener("scrollend", onScrollEnd);
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    };
  }, [settleScroll]);

  if (displayedLength === 0) {
    return (
      <div className="wheel wheel--empty" style={{ width, height }}>
        <div className="wheel-selection" style={{ height: itemHeight, marginTop: padY }} />
      </div>
    );
  }

  return (
    <div className="wheel" style={{ width, height, ["--wheel-item-height" as string]: `${itemHeight}px` }}>
      <div className="wheel-fade wheel-fade-top" aria-hidden="true" />
      <div className="wheel-fade wheel-fade-bottom" aria-hidden="true" />
      <div className="wheel-selection" style={{ height: itemHeight, marginTop: padY }} aria-hidden="true" />
      <div
        ref={listRef}
        className="wheel-list"
        style={{ paddingTop: padY, paddingBottom: padY }}
        onScroll={handleScroll}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        onMouseUp={settleScroll}
        onMouseLeave={() => {
          if (userScrollingRef.current) settleScroll();
        }}
      >
        {displayedValues.map((value, index) => {
          const visual = visuals[index] ?? wheelItemVisual(index - safeDisplayedIndex);
          return (
            <div
              key={`${value}-${index}`}
              className="wheel-item"
              style={{
                height: itemHeight,
                opacity: visual.opacity,
                transform: `scale(${visual.scale})`,
              }}
            >
              {value}
            </div>
          );
        })}
      </div>
    </div>
  );
}
