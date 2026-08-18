import { useEffect, useRef, useState, type ReactNode } from "react";

export type TopIcon = "home" | "profile" | "cart";

type TopZoneProps = {
  left: TopIcon;
  right: TopIcon;
  onLeft: () => void;
  onRight: () => void;
  topHeight: number;
};

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

export function TopZone({ left, right, onLeft, onRight, topHeight }: TopZoneProps) {
  const iconSize = topHeight * 0.8;
  const padding = topHeight * 0.1;
  return (
    <header className="top-zone" style={{ height: topHeight, padding }}>
      <button type="button" className="icon-btn" onClick={onLeft} aria-label={iconAlt[left]}>
        <img src={iconSrc[left]} alt="" style={{ width: iconSize, height: iconSize }} />
      </button>
      <button type="button" className="icon-btn" onClick={onRight} aria-label={iconAlt[right]}>
        <img src={iconSrc[right]} alt="" style={{ width: iconSize, height: iconSize }} />
      </button>
    </header>
  );
}

export function ScreenLayout({
  topHeight,
  left,
  right,
  onLeft,
  onRight,
  children,
}: TopZoneProps & { children: ReactNode }) {
  return (
    <div className="screen">
      <TopZone left={left} right={right} onLeft={onLeft} onRight={onRight} topHeight={topHeight} />
      <div className="content-zone" style={{ height: `calc(100% - ${topHeight}px)` }}>
        {children}
      </div>
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
  onChange,
}: {
  values: string[];
  selectedIndex: number;
  width: number;
  height: number;
  onChange: (index: number) => void;
}) {
  const itemHeight = height / 3;
  const listRef = useRef<HTMLDivElement>(null);
  const userScrollingRef = useRef(false);
  const settleTimerRef = useRef<number | null>(null);
  const lastEmittedIndexRef = useRef(selectedIndex);
  const safeIndex = Math.max(0, Math.min(selectedIndex, Math.max(values.length - 1, 0)));
  const [highlightIndex, setHighlightIndex] = useState(safeIndex);

  useEffect(() => {
    setHighlightIndex(safeIndex);
    lastEmittedIndexRef.current = safeIndex;
    if (userScrollingRef.current) return;
    const node = listRef.current;
    if (!node) return;
    const target = safeIndex * itemHeight;
    if (Math.abs(node.scrollTop - target) > 1) {
      node.scrollTop = target;
    }
  }, [safeIndex, itemHeight, values.join("\u0001")]);

  const emitSelection = (index: number) => {
    const clamped = Math.max(0, Math.min(index, values.length - 1));
    if (clamped === lastEmittedIndexRef.current) return;
    lastEmittedIndexRef.current = clamped;
    onChange(clamped);
  };

  const handleScroll = () => {
    const node = listRef.current;
    if (!node) return;
    userScrollingRef.current = true;
    const index = Math.round(node.scrollTop / itemHeight);
    const clamped = Math.max(0, Math.min(index, values.length - 1));
    setHighlightIndex(clamped);
    if (settleTimerRef.current !== null) window.clearTimeout(settleTimerRef.current);
    settleTimerRef.current = window.setTimeout(() => {
      userScrollingRef.current = false;
      node.scrollTop = clamped * itemHeight;
      setHighlightIndex(clamped);
      emitSelection(clamped);
    }, 120);
  };

  useEffect(
    () => () => {
      if (settleTimerRef.current !== null) window.clearTimeout(settleTimerRef.current);
    },
    [],
  );

  return (
    <div className="wheel" style={{ width, height }}>
      <div className="wheel-frame" aria-hidden="true" />
      <div
        ref={listRef}
        className="wheel-list"
        style={{ paddingTop: itemHeight, paddingBottom: itemHeight }}
        onScroll={handleScroll}
        onTouchEnd={handleScroll}
      >
        {values.map((value, index) => (
          <div
            key={`${value}-${index}`}
            className={index === highlightIndex ? "wheel-item active" : "wheel-item"}
            style={{ height: itemHeight }}
          >
            {value}
          </div>
        ))}
      </div>
    </div>
  );
}
