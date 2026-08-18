import { useEffect, useRef, useState, type ReactNode } from "react";

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
  mainMenu = false,
  children,
}: TopZoneProps & { mainMenu?: boolean; children: ReactNode }) {
  return (
    <div className={mainMenu ? "screen screen-main" : "screen"}>
      <TopZone
        left={left}
        right={right}
        onLeft={onLeft}
        onRight={onRight}
        topHeight={topHeight}
        cartCount={cartCount}
      />
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
  variant = "default",
}: {
  text: string;
  width: number;
  height: number;
  fontSize: number;
  onClick: () => void;
  enabled?: boolean;
  onOverflow?: () => void;
  variant?: "default" | "nav" | "action";
}) {
  const variantClass =
    variant === "nav" ? "menu-btn menu-btn--nav" : variant === "action" ? "menu-btn menu-btn--action" : "menu-btn";
  const resolvedFontSize = variant === "default" ? fontSize * 1.5 : fontSize;

  return (
    <button
      type="button"
      className={variantClass}
      style={{ width, height, fontSize: resolvedFontSize }}
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
