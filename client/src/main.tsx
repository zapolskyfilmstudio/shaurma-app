import { StrictMode, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { createRoot } from "react-dom/client";
import { api } from "./api";
import { MenuButton, ScreenLayout, WheelPicker } from "./components";
import { wheelMetrics } from "./wheelPicker";
import {
  clearCartDraft,
  clearPendingOrderId,
  getDeviceId,
  getPendingOrderId,
  loadCartDraft,
  loadProfile,
  saveCart,
  saveCartDraft,
  saveProfile,
  setPendingOrderId,
} from "./storage";
import {
  allowedDates,
  allowedHourRange,
  allowedMinuteRange,
  canSubmitOrderForSelectedDate,
  findEarliestValidSlot,
  formatDateTime,
  formatMoney,
  isDateAllowed,
  isSameMoscowDate,
  monthNameRu,
  moscowToMs,
  normalizeMenuName,
  normalizeRequestedTime,
  pickupBoundsForDate,
  scheduleForDate,
  toMoscowParts,
} from "./timeRules";
import {
  isHistoryManagedRoute,
  pushHistoryRoute,
  readRouteFromLocation,
  resetHistoryToMain,
  seedHistoryStack,
  seedInitialBrowserHistory,
  type AppRoute as Route,
} from "./navigationHistory";
import type {
  CartItem,
  CategoryDto,
  ClientProfile,
  DayScheduleDto,
  MenuItemDto,
  OrderDto,
  SbpBankDto,
} from "./types";
import "./styles.css";

seedInitialBrowserHistory();

const MENU_BUTTONS = [
  "ШАУРМА",
  "ГРИЛЬ НА УГЛЯХ",
  "КАРТОШКА & СНЕКИ",
  "НАПИТКИ",
  "ДОПОЛНИТЕЛЬНО",
  "КОМБО & АКЦИИ",
  "ОБРАТНАЯ СВЯЗЬ",
  "МОИ ЗАКАЗЫ",
];

function isShawarmaCategory(categoryName: string): boolean {
  return normalizeMenuName(categoryName) === normalizeMenuName("ШАУРМА");
}

function isInstantCategory(categoryName: string): boolean {
  const name = normalizeMenuName(categoryName);
  return name === normalizeMenuName("НАПИТКИ") || name === normalizeMenuName("ДОПОЛНИТЕЛЬНО");
}

function formatMenuItemMeta(item: MenuItemDto, categoryName: string): string {
  const parts = [formatMoney(item.price), `${item.weight} г`];
  if (!isInstantCategory(categoryName) && item.cooking_time > 0) {
    parts.push(`${item.cooking_time} мин`);
  }
  return parts.join(" · ");
}

const SHAWARMA_REMOVAL_ORDER = ["капуста", "морковь", "помидор", "огурец"];

function sortShawarmaRemovals(removals: MenuItemDto["removals"]): MenuItemDto["removals"] {
  return [...removals].sort((left, right) => {
    const leftIndex = SHAWARMA_REMOVAL_ORDER.indexOf(left.name.toLowerCase());
    const rightIndex = SHAWARMA_REMOVAL_ORDER.indexOf(right.name.toLowerCase());
    return (leftIndex === -1 ? 999 : leftIndex) - (rightIndex === -1 ? 999 : rightIndex);
  });
}

function formatNeKlastLine(removalNames: string): string | null {
  const trimmed = removalNames.trim();
  return trimmed ? `НЕ КЛАСТЬ: ${trimmed}` : null;
}

function toggleRemovalId(current: number[], removalId: number): number[] {
  return current.includes(removalId) ? current.filter((id) => id !== removalId) : [...current, removalId];
}

function paymentStatusLabel(status: string): string {
  switch (status) {
    case "WAITING":
      return "Ожидает оплаты";
    case "FAILED":
      return "Оплата не прошла";
    case "PAID":
      return "Оплачен";
    case "CANCELLED":
      return "Отменён";
    default:
      return status;
  }
}

function isUnpaidPaymentStatus(status: string): boolean {
  return status === "WAITING" || status === "FAILED";
}

function useViewport() {
  const [size, setSize] = useState({ width: window.innerWidth, height: window.innerHeight });
  useEffect(() => {
    const onResize = () => setSize({ width: window.innerWidth, height: window.innerHeight });
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);
  return size;
}

function App() {
  const viewport = useViewport();
  const topHeight = viewport.height * 0.15;
  const buttonWidth = viewport.width * 0.8;
  const buttonHeight = viewport.height * 0.85 * 0.08;
  const buttonGap = viewport.height * 0.85 * 0.03;

  const [route, setRoute] = useState<Route>({ name: "startup" });
  const skipHistorySync = useRef(false);
  const [message, setMessage] = useState("Загружаем...");
  const [profile, setProfile] = useState<ClientProfile | null>(loadProfile());
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [cart, setCart] = useState<CartItem[]>(() => loadCartDraft().items);
  const [weeklySchedule, setWeeklySchedule] = useState<DayScheduleDto[]>([]);
  const [paymentEnabled, setPaymentEnabled] = useState(false);
  const [paymentSkip, setPaymentSkip] = useState(false);
  const [serverOffset, setServerOffset] = useState(profile?.serverTimeOffsetMs ?? 0);

  const serverNow = useCallback(() => Date.now() + serverOffset, [serverOffset]);

  const persistCart = useCallback((items: CartItem[]) => {
    setCart(items);
    saveCart(items);
  }, []);

  const navigate = useCallback((next: Route) => {
    if (!isHistoryManagedRoute(next)) {
      setRoute(next);
      return;
    }
    skipHistorySync.current = true;
    pushHistoryRoute(next);
    setRoute(next);
  }, []);

  const finishBootstrap = useCallback((next: Route) => {
    if (!isHistoryManagedRoute(next)) {
      setRoute(next);
      return;
    }
    skipHistorySync.current = true;
    seedHistoryStack(next);
    setRoute(next);
  }, []);

  const goMain = useCallback(() => {
    skipHistorySync.current = true;
    resetHistoryToMain();
    setRoute({ name: "main" });
  }, []);

  const goCart = useCallback(() => navigate({ name: "cart" }), [navigate]);
  const goProfile = useCallback(() => navigate({ name: "profile" }), [navigate]);
  const goBack = useCallback(() => {
    window.history.back();
  }, []);

  useEffect(() => {
    const syncRouteFromBrowser = (state?: unknown) => {
      skipHistorySync.current = true;
      setRoute(readRouteFromLocation(state));
    };

    const onPopState = (event: PopStateEvent) => {
      syncRouteFromBrowser(event.state);
    };

    const onHashChange = () => {
      if (skipHistorySync.current) {
        skipHistorySync.current = false;
        return;
      }
      syncRouteFromBrowser();
    };

    window.addEventListener("popstate", onPopState);
    window.addEventListener("hashchange", onHashChange);
    return () => {
      window.removeEventListener("popstate", onPopState);
      window.removeEventListener("hashchange", onHashChange);
    };
  }, []);

  const bootstrap = useCallback(async () => {
    setMessage("Подключаемся...");
    const deviceId = getDeviceId();
    const [init, config, menu] = await Promise.all([api.init(deviceId), api.config(), api.menu()]);
    const offset = init.server_time - Date.now();
    const nextProfile: ClientProfile = {
      deviceId,
      clientNumber: init.client_number,
      name: init.name,
      phone: init.phone,
      isBlocked: init.is_blocked,
      serverTimeOffsetMs: offset,
    };
    setServerOffset(offset);
    setProfile(nextProfile);
    saveProfile(nextProfile);
    setWeeklySchedule(config.weekly_schedule ?? []);
    setPaymentEnabled(config.payment_enabled);
    setPaymentSkip(config.payment_skip);
    setCategories(menu.categories);
    if (init.is_blocked) {
      finishBootstrap({ name: "blocked" });
      return;
    }
    const params = new URLSearchParams(window.location.search);
    const paymentResult = params.get("payment");
    const pendingOrder = getPendingOrderId();
    if ((paymentResult === "success" || paymentResult === "fail") && pendingOrder) {
      finishBootstrap({ name: "payment", result: paymentResult, publicId: pendingOrder });
      return;
    }
    if (loadCartDraft().items.length > 0) {
      finishBootstrap({ name: "cart" });
      return;
    }
    finishBootstrap({ name: "main" });
  }, [finishBootstrap]);

  useEffect(() => {
    void bootstrap().catch((error) => setMessage(error instanceof Error ? error.message : "Ошибка загрузки"));
  }, [bootstrap]);

  const findCategoryByTitle = (title: string) =>
    categories.find((category) => normalizeMenuName(category.name) === normalizeMenuName(title));

  const allItems = useMemo(
    () => categories.flatMap((category) => category.items.map((item) => ({ ...item, category }))),
    [categories],
  );

  const cartCount = cart.length;

  const goOrdersAfterPaymentError = useCallback(() => {
    clearPendingOrderId();
    navigate({ name: "orders" });
  }, [navigate]);

  const finishPaidOrder = useCallback(() => {
    clearCartDraft();
    setCart([]);
    clearPendingOrderId();
    navigate({ name: "orders" });
  }, [navigate]);

  if (route.name === "startup") {
    return (
      <div className="screen centered-column">
        <div className="spinner" />
        <p>{message}</p>
      </div>
    );
  }

  if (route.name === "blocked") {
    return (
      <div className="screen centered-column">
        <h2>Доступ заблокирован</h2>
        <p className="text-muted">Обратитесь к администратору, чтобы разблокировать устройство.</p>
      </div>
    );
  }

  if (route.name === "main") {
    return (
      <MainScreen
        topHeight={topHeight}
        buttonWidth={buttonWidth}
        buttonHeight={buttonHeight}
        buttonGap={buttonGap}
        onProfile={goProfile}
        onCart={goCart}
        onCategory={(categoryId) => navigate({ name: "category", categoryId })}
        onMissing={(title) => navigate({ name: "missing", title })}
        onOrders={() => navigate({ name: "orders" })}
        findCategoryByTitle={findCategoryByTitle}
        cartCount={cartCount}
      />
    );
  }

  if (route.name === "missing") {
    return (
      <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={goMain} onRight={goCart} cartCount={cartCount}>
        <div className="centered-column">
          <h2>{route.title}</h2>
          <p className="text-muted">Скоро будет</p>
        </div>
      </ScreenLayout>
    );
  }

  if (route.name === "category") {
    const category = categories.find((item) => item.id === route.categoryId);
    const items = category?.items.filter((item) => item.is_active) ?? [];
    return (
      <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={goMain} onRight={goCart} cartCount={cartCount}>
        <div className="category-screen">
          <h2>{category?.name ?? "Категория"}</h2>
          {items.map((item) => (
            <button
              key={item.id}
              type="button"
              className="card"
              onClick={() => navigate({ name: "product", itemId: item.id })}
            >
              <strong>{item.name}</strong>
              {item.description && <p className="text-muted">{item.description}</p>}
              <p>{formatMenuItemMeta(item, category?.name ?? "")}</p>
            </button>
          ))}
        </div>
      </ScreenLayout>
    );
  }

  if (route.name === "product") {
    const productItem = allItems.find((entry) => entry.id === route.itemId) ?? null;
    return (
      <ProductScreen
        item={productItem}
        topHeight={topHeight}
        viewport={viewport}
        buttonWidth={buttonWidth}
        buttonHeight={buttonHeight}
        onBack={() => {
          if (productItem) goBack();
          else goMain();
        }}
        onAdded={(item) => {
          persistCart([...cart, item]);
          goMain();
        }}
        goMain={goMain}
        goCart={goCart}
        cartCount={cartCount}
      />
    );
  }

  if (route.name === "cart") {
    return (
      <CartScreen
        cart={cart}
        topHeight={topHeight}
        viewport={viewport}
        serverNow={serverNow}
        weeklySchedule={weeklySchedule}
        onHome={goMain}
        onProfile={goProfile}
        onEdit={(menuItemId) => {
          persistCart(cart.filter((entry) => entry.menuItemId !== menuItemId));
          navigate({ name: "product", itemId: menuItemId });
        }}
        onCartChange={persistCart}
        onCheckout={(publicId, totalPrice) => {
          setPendingOrderId(publicId);
          navigate({ name: "checkout", publicId, totalPrice });
        }}
        onPaid={finishPaidOrder}
        paymentEnabled={paymentEnabled}
        paymentSkip={paymentSkip}
      />
    );
  }

  if (route.name === "checkout") {
    return (
      <CheckoutScreen
        topHeight={topHeight}
        viewport={viewport}
        publicId={route.publicId}
        totalPrice={route.totalPrice}
        onHome={goMain}
        onOpenSbp={(publicId, totalPrice) => navigate({ name: "sbpBanks", publicId, totalPrice })}
        onPaid={finishPaidOrder}
        onFailed={goOrdersAfterPaymentError}
      />
    );
  }

  if (route.name === "sbpBanks") {
    return (
      <SbpBanksScreen
        topHeight={topHeight}
        viewport={viewport}
        publicId={route.publicId}
        onHome={goMain}
        onPaid={finishPaidOrder}
        onFailed={goOrdersAfterPaymentError}
      />
    );
  }

  if (route.name === "payment") {
    return (
      <PaymentScreen
        topHeight={topHeight}
        buttonWidth={buttonWidth}
        buttonHeight={buttonHeight}
        result={route.result}
        publicId={route.publicId}
        onHome={goMain}
        onPaid={finishPaidOrder}
        onFailed={goOrdersAfterPaymentError}
        cartCount={cartCount}
      />
    );
  }

  if (route.name === "orders") {
    return (
      <OrdersScreen
        topHeight={topHeight}
        viewport={viewport}
        onHome={goMain}
        onCart={goCart}
        onCheckout={(publicId, totalPrice) => {
          setPendingOrderId(publicId);
          navigate({ name: "checkout", publicId, totalPrice });
        }}
        paymentEnabled={paymentEnabled}
        cartCount={cartCount}
      />
    );
  }

  return (
    <ProfileScreen
      profile={profile}
      topHeight={topHeight}
      buttonWidth={buttonWidth}
      buttonHeight={buttonHeight}
      onHome={goMain}
      onCart={goCart}
      onSaved={(next) => {
        setProfile(next);
        saveProfile(next);
      }}
      cartCount={cartCount}
    />
  );
}

function MainScreen({
  topHeight,
  buttonWidth,
  buttonHeight,
  buttonGap,
  onProfile,
  onCart,
  onCategory,
  onMissing,
  onOrders,
  findCategoryByTitle,
  cartCount,
}: {
  topHeight: number;
  buttonWidth: number;
  buttonHeight: number;
  buttonGap: number;
  onProfile: () => void;
  onCart: () => void;
  onCategory: (categoryId: number) => void;
  onMissing: (title: string) => void;
  onOrders: () => void;
  findCategoryByTitle: (title: string) => CategoryDto | undefined;
  cartCount: number;
}) {
  const [fontSize, setFontSize] = useState(28);
  return (
    <ScreenLayout topHeight={topHeight} left="profile" right="cart" onLeft={onProfile} onRight={onCart} cartCount={cartCount}>
      <div className="centered-column" style={{ gap: buttonGap }}>
        {MENU_BUTTONS.map((title) => (
          <MenuButton
            key={title}
            text={title}
            width={buttonWidth}
            height={buttonHeight}
            fontSize={fontSize}
            onOverflow={() => setFontSize((value) => value * 0.9)}
            onClick={() => {
              if (title === "МОИ ЗАКАЗЫ") {
                onOrders();
                return;
              }
              if (title === "КОМБО & АКЦИИ" || title === "ОБРАТНАЯ СВЯЗЬ") {
                onMissing(title);
                return;
              }
              const category = findCategoryByTitle(title);
              if (!category) onMissing(title);
              else onCategory(category.id);
            }}
          />
        ))}
        <p className="seo-footer text-muted">МегаШаверма · официальный сайт мегашаверма.рф</p>
      </div>
    </ScreenLayout>
  );
}

function ProductScreen({
  item,
  topHeight,
  viewport,
  buttonWidth,
  buttonHeight,
  onBack,
  onAdded,
  goMain,
  goCart,
  cartCount,
}: {
  item: (MenuItemDto & { category: CategoryDto }) | null;
  topHeight: number;
  viewport: { width: number; height: number };
  buttonWidth: number;
  buttonHeight: number;
  onBack: () => void;
  onAdded: (item: CartItem) => void;
  goMain: () => void;
  goCart: () => void;
  cartCount: number;
}) {
  const resolved = item;
  const [selectedRemovals, setSelectedRemovals] = useState<number[]>([]);
  const [showRemovals, setShowRemovals] = useState(false);
  const [adding, setAdding] = useState(false);

  if (!resolved) {
    return (
      <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={goMain} onRight={goCart} cartCount={cartCount}>
        <div className="centered-column">
          <div className="spinner" />
          <p>Загружаем товар...</p>
        </div>
      </ScreenLayout>
    );
  }

  const categoryName = resolved.category.name;
  const showRemovalsOption = isShawarmaCategory(categoryName);
  const contentHeight = viewport.height - topHeight;
  const modalWidth = Math.round(viewport.width * 0.75);
  const modalHeight = Math.round(contentHeight * 0.75);
  const doneButtonWidth = Math.round(modalWidth * 0.55);

  const totalPrice = resolved.price;

  const addToCart = () => {
    setAdding(true);
    const cartItem: CartItem = {
      id: crypto.randomUUID(),
      menuItemId: resolved.id,
      name: resolved.name,
      price: resolved.price,
      weight: resolved.weight,
      cookingTime: resolved.cooking_time,
      additionsIds: [],
      removalsIds: selectedRemovals,
      additionNames: "",
      removalNames: resolved.removals
        .filter((removal) => selectedRemovals.includes(removal.id))
        .map((removal) => removal.name)
        .join(", "),
      totalPrice,
    };
    onAdded(cartItem);
    setAdding(false);
  };

  const productMeta = isInstantCategory(categoryName)
    ? `${resolved.weight} г`
    : `${resolved.weight} г · готовится ${resolved.cooking_time} мин`;

  return (
    <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={goMain} onRight={goCart} cartCount={cartCount}>
      <div className="product-screen">
        <button type="button" className="menu-btn" style={{ width: 120, height: 40 }} onClick={onBack}>
          НАЗАД
        </button>
        <h2>{resolved.name}</h2>
        {resolved.description && <p className="text-muted">{resolved.description}</p>}
        <p>{productMeta}</p>
        <p>Цена: {formatMoney(totalPrice)}</p>
        {showRemovalsOption && (
          <MenuButton text="НЕ КЛАСТЬ" width={buttonWidth} height={buttonHeight} fontSize={18} onClick={() => setShowRemovals(true)} />
        )}
        <MenuButton
          text={adding ? "Добавляем..." : "Добавить в корзину"}
          width={buttonWidth}
          height={buttonHeight}
          fontSize={20}
          enabled={!adding}
          onClick={addToCart}
        />
      </div>
      {showRemovalsOption &&
        showRemovals &&
        createPortal(
          <div
            className="removal-modal-backdrop"
            style={{ top: topHeight, height: contentHeight }}
            onClick={() => setShowRemovals(false)}
          >
            <div
              className="removal-modal-panel"
              style={{ width: modalWidth, height: modalHeight }}
              onClick={(event) => event.stopPropagation()}
            >
              <div className="removal-modal-header">НЕ КЛАСТЬ</div>
              <div className="removal-modal-body">
                {sortShawarmaRemovals(resolved.removals).map((removal) => {
                  const selected = selectedRemovals.includes(removal.id);
                  return (
                    <button
                      key={removal.id}
                      type="button"
                      className="removal-option"
                      onClick={() => setSelectedRemovals((current) => toggleRemovalId(current, removal.id))}
                    >
                      <span className={`removal-toggle${selected ? " is-selected" : ""}`} aria-hidden="true" />
                      <span className="removal-option-label">{removal.name}</span>
                    </button>
                  );
                })}
              </div>
              <div className="removal-modal-footer">
                <MenuButton
                  text="Готово"
                  width={doneButtonWidth}
                  height={44}
                  fontSize={18}
                  onClick={() => setShowRemovals(false)}
                />
              </div>
            </div>
          </div>,
          document.body,
        )}
    </ScreenLayout>
  );
}

function CartScreen({
  cart,
  topHeight,
  viewport,
  serverNow,
  weeklySchedule,
  onHome,
  onProfile,
  onEdit,
  onCartChange,
  onCheckout,
  onPaid,
  paymentEnabled,
  paymentSkip,
}: {
  cart: CartItem[];
  topHeight: number;
  viewport: { width: number; height: number };
  serverNow: () => number;
  weeklySchedule: DayScheduleDto[];
  onHome: () => void;
  onProfile: () => void;
  onEdit: (menuItemId: number) => void;
  onCartChange: (items: CartItem[]) => void;
  onCheckout: (publicId: string, totalPrice: number) => void;
  onPaid: () => void;
  paymentEnabled: boolean;
  paymentSkip: boolean;
}) {
  const canPlaceOrder = paymentEnabled || paymentSkip;
  const initialDraft = useMemo(() => loadCartDraft(), []);
  const contentWidth = viewport.width * 0.9;
  const buttonWidth = viewport.width * 0.8;
  const buttonHeight = viewport.height * 0.85 * 0.075;
  const wheels = wheelMetrics(viewport.width);
  const [comment, setComment] = useState(initialDraft.comment);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [fontSize, setFontSize] = useState(22);
  const userPickedTime = useRef(initialDraft.requestedTime != null);
  const maxCooking = cart.length ? Math.max(...cart.map((item) => item.cookingTime)) : 0;
  const scheduleReady = weeklySchedule.length === 7;

  const [requestedTime, setRequestedTime] = useState<number>(() => initialDraft.requestedTime ?? 0);

  useEffect(() => {
    if (!scheduleReady) return;
    setRequestedTime((current) =>
      normalizeRequestedTime(current || findEarliestValidSlot(serverNow(), maxCooking, weeklySchedule), serverNow(), maxCooking, weeklySchedule),
    );
  }, [scheduleReady, weeklySchedule, maxCooking, serverNow]);

  useEffect(() => {
    saveCartDraft({ items: cart, requestedTime, comment });
  }, [cart, requestedTime, comment]);

  useEffect(() => {
    if (!scheduleReady) return;
    const sync = () => {
      void api.config().then((config) => {
        const now = config.server_time;
        const schedule = config.weekly_schedule ?? weeklySchedule;
        if (!userPickedTime.current) {
          setRequestedTime(findEarliestValidSlot(now, maxCooking, schedule));
          return;
        }
        setRequestedTime((current) => normalizeRequestedTime(current, now, maxCooking, schedule));
      });
    };
    sync();
    const timer = window.setInterval(sync, 8000);
    return () => window.clearInterval(timer);
  }, [maxCooking, scheduleReady, weeklySchedule]);

  const nowMs = serverNow();
  const selected = toMoscowParts(requestedTime);
  const dates = scheduleReady ? allowedDates(nowMs, maxCooking, weeklySchedule) : [];
  const selectedInAllowed = dates.some((date) => isSameMoscowDate(date, selected));
  const effectiveSelected = selectedInAllowed && dates.length > 0 ? selected : dates[0] ?? selected;
  const selectedBounds = scheduleReady
    ? pickupBoundsForDate(effectiveSelected, nowMs, maxCooking, weeklySchedule)
    : { minMs: requestedTime, maxMs: requestedTime, canAcceptOrderToday: true, isValid: false };
  const years = [...new Set(dates.map((date) => date.year))];
  const months = dates.filter((date) => date.year === effectiveSelected.year).map((date) => date.month);
  const days = dates
    .filter((date) => date.year === effectiveSelected.year && date.month === effectiveSelected.month)
    .map((date) => date.day);
  const hourRange = selectedBounds.isValid
    ? allowedHourRange(effectiveSelected, selectedBounds.minMs, selectedBounds.maxMs)
    : [];
  const displayTime = toMoscowParts(requestedTime);
  const pickerHour = selectedInAllowed ? displayTime.hour : (hourRange[0] ?? toMoscowParts(selectedBounds.minMs).hour);
  const minuteRange = selectedBounds.isValid
    ? allowedMinuteRange(effectiveSelected, pickerHour, selectedBounds.minMs, selectedBounds.maxMs)
    : [];
  const isTimeValid =
    scheduleReady &&
    selectedBounds.isValid &&
    selectedInAllowed &&
    requestedTime >= selectedBounds.minMs &&
    requestedTime <= selectedBounds.maxMs &&
    canSubmitOrderForSelectedDate(effectiveSelected, nowMs, weeklySchedule);
  const selectedDaySchedule = scheduleReady ? scheduleForDate(effectiveSelected, weeklySchedule) : null;

  useEffect(() => {
    if (!scheduleReady || !selectedBounds.isValid) return;
    if (!selectedInAllowed || requestedTime < selectedBounds.minMs || requestedTime > selectedBounds.maxMs) {
      setRequestedTime(normalizeRequestedTime(requestedTime, serverNow(), maxCooking, weeklySchedule));
    }
  }, [scheduleReady, selectedInAllowed, selectedBounds.isValid, selectedBounds.minMs, selectedBounds.maxMs, requestedTime, maxCooking, weeklySchedule, serverNow]);

  const setDate = (year: number, month: number, day: number) => {
    userPickedTime.current = true;
    const current = toMoscowParts(requestedTime);
    const next = moscowToMs(year, month, day, current.hour, current.minute);
    setRequestedTime(normalizeRequestedTime(next, serverNow(), maxCooking, weeklySchedule));
  };

  const setHour = (hour: number) => {
    userPickedTime.current = true;
    const current = toMoscowParts(requestedTime);
    const bounds = pickupBoundsForDate(current, serverNow(), maxCooking, weeklySchedule);
    const minutes = allowedMinuteRange(current, hour, bounds.minMs, bounds.maxMs);
    const minute = minutes.includes(current.minute) ? current.minute : minutes[0] ?? 0;
    setRequestedTime(normalizeRequestedTime(moscowToMs(current.year, current.month, current.day, hour, minute), serverNow(), maxCooking, weeklySchedule));
  };

  const setMinute = (minute: number) => {
    userPickedTime.current = true;
    const current = toMoscowParts(requestedTime);
    setRequestedTime(
      normalizeRequestedTime(
        moscowToMs(current.year, current.month, current.day, current.hour, minute),
        serverNow(),
        maxCooking,
        weeklySchedule,
      ),
    );
  };

  const submit = async () => {
    if (!isTimeValid) return;
    setSubmitting(true);
    setError(null);
    const orderTotal = cart.reduce((sum, item) => sum + item.totalPrice, 0);
    try {
      const result = await api.createOrder({
        requested_time: requestedTime,
        general_comment: comment || null,
        items: cart.map((item) => ({
          menu_item_id: item.menuItemId,
          additions_ids: item.additionsIds,
          removals_ids: item.removalsIds,
        })),
      });
      if (result.payment_status === "WAITING") {
        onCheckout(result.public_id, orderTotal);
        return;
      }
      if (result.payment_status === "PAID" && paymentSkip && !paymentEnabled) {
        onPaid();
        return;
      }
      if (result.payment_status === "PAID") {
        onPaid();
        return;
      }
      setError("Не удалось оформить заказ. Попробуйте позже.");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось создать заказ");
    } finally {
      setSubmitting(false);
    }
  };

  if (cart.length === 0) {
    return (
      <ScreenLayout topHeight={topHeight} left="home" right="profile" onLeft={onHome} onRight={onProfile}>
        <div className="centered-column">
          <p>Корзина пустая</p>
        </div>
      </ScreenLayout>
    );
  }

  return (
    <ScreenLayout topHeight={topHeight} left="home" right="profile" onLeft={onHome} onRight={onProfile}>
      <div className="list-screen">
        {cart.map((item) => {
          const neKlastLine = formatNeKlastLine(item.removalNames);
          return (
          <div key={item.id} className="card" style={{ width: contentWidth }}>
            <strong>{item.name}</strong>
            {neKlastLine && <p>{neKlastLine}</p>}
            <p>
              Вес: {item.weight} г · Цена: {formatMoney(item.totalPrice)}
            </p>
            <div className="row-buttons">
              <MenuButton
                text="Редактировать"
                width={contentWidth * 0.42}
                height={buttonHeight}
                fontSize={fontSize}
                onOverflow={() => setFontSize((value) => value * 0.9)}
                onClick={() => onEdit(item.menuItemId)}
              />
              <MenuButton
                text="Удалить"
                width={contentWidth * 0.42}
                height={buttonHeight}
                fontSize={fontSize}
                onClick={() => onCartChange(cart.filter((entry) => entry.id !== item.id))}
              />
            </div>
          </div>
          );
        })}
        <p style={{ width: contentWidth }}>Итого: {formatMoney(cart.reduce((sum, item) => sum + item.totalPrice, 0))}</p>
        <textarea
          className="textarea"
          style={{ width: contentWidth }}
          placeholder="Комментарий к заказу"
          value={comment}
          onChange={(event) => setComment(event.target.value)}
        />
        <p style={{ width: contentWidth, textAlign: "center" }}>Выберите дату и время когда должен быть готов заказ</p>
        <div className="wheel-row" style={{ width: contentWidth }}>
          <WheelPicker
            key={`hour-${hourRange.join(",")}`}
            values={hourRange.map((hour) => String(hour).padStart(2, "0"))}
            selectedIndex={Math.max(0, hourRange.indexOf(displayTime.hour))}
            width={wheels.hourWidth}
            height={wheels.height}
            itemHeight={wheels.itemHeight}
            onChange={(index) => setHour(hourRange[index])}
          />
          <span className="wheel-separator" style={{ fontSize: wheels.separatorSize }}>
            :
          </span>
          <WheelPicker
            key={`minute-${displayTime.hour}-${minuteRange.join(",")}`}
            values={minuteRange.map((minute) => String(minute).padStart(2, "0"))}
            selectedIndex={Math.max(0, minuteRange.indexOf(displayTime.minute))}
            width={wheels.minuteWidth}
            height={wheels.height}
            itemHeight={wheels.itemHeight}
            onChange={(index) => setMinute(minuteRange[index])}
          />
        </div>
        <div className="wheel-row" style={{ width: contentWidth }}>
          <WheelPicker
            values={days.map((day) => String(day).padStart(2, "0"))}
            selectedIndex={Math.max(0, days.indexOf(displayTime.day))}
            width={wheels.dayWidth}
            height={wheels.height}
            itemHeight={wheels.itemHeight}
            onChange={(index) => setDate(displayTime.year, displayTime.month, days[index])}
          />
          <WheelPicker
            values={months.map((month) => monthNameRu(month))}
            selectedIndex={Math.max(0, months.indexOf(displayTime.month))}
            width={wheels.monthWidth}
            height={wheels.height}
            itemHeight={wheels.itemHeight}
            onChange={(index) => setDate(displayTime.year, months[index], displayTime.day)}
          />
          <WheelPicker
            values={years.map(String)}
            selectedIndex={Math.max(0, years.indexOf(displayTime.year))}
            width={wheels.yearWidth}
            height={wheels.height}
            itemHeight={wheels.itemHeight}
            onChange={(index) => setDate(years[index], displayTime.month, displayTime.day)}
          />
        </div>
        {selectedBounds.isValid && (
          <p className="text-muted" style={{ width: contentWidth, textAlign: "center" }}>
            Минимум: {formatDateTime(selectedBounds.minMs)} · максимум: {formatDateTime(selectedBounds.maxMs)}
          </p>
        )}
        {!isTimeValid && (
          <p className="text-error" style={{ width: contentWidth, textAlign: "center" }}>
            {selectedDaySchedule
              ? `Выберите время от ${selectedDaySchedule.open_time} + ${maxCooking} мин до ${selectedDaySchedule.last_order_time} + ${maxCooking} мин. На сегодня заказ принимаем до ${selectedDaySchedule.last_order_time}.`
              : "Загружаем расписание..."}
          </p>
        )}
        {error && <p className="text-error">{error}</p>}
        {!canPlaceOrder && (
          <p className="text-error" style={{ width: contentWidth, textAlign: "center" }}>
            Онлайн-оплата не настроена. Укажите TBANK_TERMINAL_KEY и TBANK_PASSWORD в .env на сервере.
          </p>
        )}
        {paymentSkip && !paymentEnabled && (
          <p className="text-muted" style={{ width: contentWidth, textAlign: "center" }}>
            Тестовый режим: заказ без онлайн-оплаты.
          </p>
        )}
        <MenuButton text="Добавить к заказу" width={buttonWidth} height={buttonHeight} fontSize={fontSize} onClick={onHome} />
        <MenuButton
          text={submitting ? "Оформляем..." : "Оформить заказ"}
          width={buttonWidth}
          height={buttonHeight}
          fontSize={fontSize}
          enabled={isTimeValid && !submitting && canPlaceOrder}
          onClick={() => void submit()}
        />
      </div>
    </ScreenLayout>
  );
}

function CheckoutScreen({
  topHeight,
  viewport,
  publicId,
  totalPrice,
  onHome,
  onOpenSbp,
  onPaid,
  onFailed,
}: {
  topHeight: number;
  viewport: { width: number; height: number };
  publicId: string;
  totalPrice: number;
  onHome: () => void;
  onOpenSbp: (publicId: string, totalPrice: number) => void;
  onPaid: () => void;
  onFailed: () => void;
}) {
  const contentWidth = viewport.width * 0.9;
  const optionMinHeight = viewport.height * 0.85 * 0.16;
  const [paymentUrl, setPaymentUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState("Выберите способ оплаты");
  const finishedRef = useRef(false);

  const finishPaid = useCallback(() => {
    if (finishedRef.current) return;
    finishedRef.current = true;
    onPaid();
  }, [onPaid]);

  const finishFailed = useCallback(() => {
    if (finishedRef.current) return;
    finishedRef.current = true;
    onFailed();
  }, [onFailed]);

  useEffect(() => {
    setPendingOrderId(publicId);
    let cancelled = false;
    const init = async () => {
      setLoading(true);
      setError(null);
      try {
        const payment = await api.retryPayment(publicId);
        if (cancelled) return;
        if (payment.payment_status === "PAID") {
          setStatusMessage("Оплата подтверждена");
          finishPaid();
          return;
        }
        setPaymentUrl(payment.payment_url ?? null);
      } catch (caught) {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : "Не удалось подготовить оплату");
          finishFailed();
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void init();
    return () => {
      cancelled = true;
    };
  }, [publicId, finishPaid, finishFailed]);

  useEffect(() => {
    let cancelled = false;
    let timer = 0;
    const poll = async () => {
      try {
        const status = await api.getPaymentStatus(publicId);
        if (cancelled || finishedRef.current) return;
        if (status.payment_status === "PAID") {
          setStatusMessage("Оплата подтверждена");
          finishPaid();
          return;
        }
        if (status.payment_status === "FAILED") {
          setStatusMessage("Оплата не прошла");
          finishFailed();
          return;
        }
        timer = window.setTimeout(poll, 2000);
      } catch {
        if (!cancelled && !finishedRef.current) {
          timer = window.setTimeout(poll, 3000);
        }
      }
    };
    void poll();
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [publicId, finishPaid, finishFailed]);

  const openCard = () => {
    if (!paymentUrl) {
      setError("Не удалось получить ссылку на оплату картой");
      finishFailed();
      return;
    }
    setStatusMessage("Переходим к оплате картой...");
    window.location.href = paymentUrl;
  };

  return (
    <ScreenLayout topHeight={topHeight} left="home" right="home" onLeft={onHome} onRight={onHome}>
      <div className="checkout-screen" style={{ width: contentWidth }}>
        <h2>Оплата заказа {publicId}</h2>
        <p className="checkout-amount">{formatMoney(totalPrice)}</p>
        <p className="text-muted">{statusMessage}</p>
        {loading && <div className="spinner" />}
        <button
          type="button"
          className="checkout-option"
          style={{ minHeight: optionMinHeight }}
          disabled={loading}
          onClick={() => onOpenSbp(publicId, totalPrice)}
        >
          Система Быстрых Платежей
        </button>
        <button
          type="button"
          className="checkout-option"
          style={{ minHeight: optionMinHeight }}
          disabled={loading || !paymentUrl}
          onClick={openCard}
        >
          Перевод по карте
        </button>
        {error && <p className="text-error">{error}</p>}
      </div>
    </ScreenLayout>
  );
}

function SbpBanksScreen({
  topHeight,
  viewport,
  publicId,
  onHome,
  onPaid,
  onFailed,
}: {
  topHeight: number;
  viewport: { width: number; height: number };
  publicId: string;
  onHome: () => void;
  onPaid: () => void;
  onFailed: () => void;
}) {
  const contentWidth = viewport.width * 0.9;
  const [banks, setBanks] = useState<SbpBankDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState("Выберите банк для оплаты через СБП");
  const [searchQuery, setSearchQuery] = useState("");
  const finishedRef = useRef(false);

  const finishPaid = useCallback(() => {
    if (finishedRef.current) return;
    finishedRef.current = true;
    onPaid();
  }, [onPaid]);

  const finishFailed = useCallback(() => {
    if (finishedRef.current) return;
    finishedRef.current = true;
    onFailed();
  }, [onFailed]);

  const filteredBanks = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return banks;
    return banks.filter((bank) => bank.bank_name.toLowerCase().includes(query));
  }, [banks, searchQuery]);

  useEffect(() => {
    setPendingOrderId(publicId);
    let cancelled = false;
    const loadBanks = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await api.getSbpBanks(publicId);
        if (cancelled) return;
        setBanks(response.banks);
        if (response.banks.length === 0) {
          const link = await api.getSbpLink(publicId);
          window.location.href = link.link;
        }
      } catch (caught) {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : "Не удалось открыть СБП");
          finishFailed();
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void loadBanks();
    return () => {
      cancelled = true;
    };
  }, [publicId, finishFailed]);

  useEffect(() => {
    let cancelled = false;
    let timer = 0;
    const poll = async () => {
      try {
        const status = await api.getPaymentStatus(publicId);
        if (cancelled || finishedRef.current) return;
        if (status.payment_status === "PAID") {
          setStatusMessage("Оплата подтверждена");
          finishPaid();
          return;
        }
        if (status.payment_status === "FAILED") {
          setStatusMessage("Оплата не прошла");
          finishFailed();
          return;
        }
        timer = window.setTimeout(poll, 2000);
      } catch {
        if (!cancelled && !finishedRef.current) {
          timer = window.setTimeout(poll, 3000);
        }
      }
    };
    void poll();
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [publicId, finishPaid, finishFailed]);

  const payWithBank = async (bankId: string) => {
    setError(null);
    setStatusMessage("Откройте приложение банка и подтвердите оплату");
    try {
      const link = await api.getSbpLink(publicId, bankId);
      window.location.href = link.link;
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось открыть оплату через СБП");
      finishFailed();
    }
  };

  return (
    <ScreenLayout topHeight={topHeight} left="home" right="home" onLeft={onHome} onRight={onHome}>
      <div className="sbp-banks-screen" style={{ width: contentWidth }}>
        <h2>Ваш Банк</h2>
        <p className="text-muted">{statusMessage}</p>
        <label className="sbp-search">
          <span className="sbp-search-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" focusable="false">
              <circle cx="11" cy="11" r="7" />
              <path d="M20 20L16 16" />
            </svg>
          </span>
          <input
            type="search"
            className="sbp-search-input"
            placeholder="Поиск банка"
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
            aria-label="Поиск банка"
          />
        </label>
        {loading && <div className="spinner" />}
        {!loading && filteredBanks.length > 0 && (
          <div className="checkout-banks">
            {filteredBanks.map((bank) => (
              <button key={bank.bank_id} type="button" className="checkout-bank" onClick={() => void payWithBank(bank.bank_id)}>
                {bank.bank_name}
              </button>
            ))}
          </div>
        )}
        {!loading && banks.length > 0 && filteredBanks.length === 0 && (
          <p className="text-muted">Банки не найдены</p>
        )}
        {error && <p className="text-error">{error}</p>}
      </div>
    </ScreenLayout>
  );
}

function OrdersScreen({
  topHeight,
  viewport,
  onHome,
  onCart,
  onCheckout,
  paymentEnabled,
  cartCount,
}: {
  topHeight: number;
  viewport: { width: number; height: number };
  onHome: () => void;
  onCart: () => void;
  onCheckout: (publicId: string, totalPrice: number) => void;
  paymentEnabled: boolean;
  cartCount: number;
}) {
  const contentWidth = viewport.width * 0.9;
  const buttonWidth = viewport.width * 0.8;
  const buttonHeight = viewport.height * 0.85 * 0.075;
  const [orders, setOrders] = useState<OrderDto[]>([]);
  const [payingId, setPayingId] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadOrders = useCallback(() => {
    void api
      .myOrders()
      .then((response) => setOrders([...response.orders].sort((left, right) => right.created_at - left.created_at)))
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    loadOrders();
    const timer = window.setInterval(loadOrders, 10_000);
    return () => window.clearInterval(timer);
  }, [loadOrders]);

  const pay = async (order: OrderDto) => {
    setPayingId(order.public_id);
    setError(null);
    try {
      setPendingOrderId(order.public_id);
      onCheckout(order.public_id, order.total_price);
    } catch (caught) {
      clearPendingOrderId();
      setError(caught instanceof Error ? caught.message : "Не удалось открыть оплату");
    } finally {
      setPayingId(null);
    }
  };

  const cancel = async (publicId: string) => {
    setCancellingId(publicId);
    setError(null);
    try {
      await api.cancelOrder(publicId);
      loadOrders();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось отменить заказ");
    } finally {
      setCancellingId(null);
    }
  };

  return (
    <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={onHome} onRight={onCart} cartCount={cartCount}>
      <div className="list-screen">
        {orders.length === 0 ? (
          <p>Заказов пока нет</p>
        ) : (
          orders.map((order) => (
            <div key={order.public_id} className="card" style={{ width: contentWidth }}>
              <p>
                <strong>{order.public_id}</strong> · {paymentStatusLabel(order.payment_status)}
              </p>
              <p>Оформлен: {formatDateTime(order.created_at)}</p>
              <p>Готовность: {formatDateTime(order.requested_time)}</p>
              {order.general_comment && <p>Комментарий: {order.general_comment}</p>}
              {order.items.map((item) => (
                <p key={item.id}>
                  {item.name_snapshot} — {formatMoney(item.price_snapshot)}
                </p>
              ))}
              <p>Итого: {formatMoney(order.total_price)}</p>
              {isUnpaidPaymentStatus(order.payment_status) && paymentEnabled && (
                <div className="row-buttons">
                  <MenuButton
                    text={payingId === order.public_id ? "Открываем..." : "Оплатить"}
                    width={contentWidth * 0.42}
                    height={buttonHeight}
                    fontSize={20}
                    enabled={payingId == null && cancellingId == null}
                    onClick={() => void pay(order)}
                  />
                  <MenuButton
                    text={cancellingId === order.public_id ? "Отмена..." : "Отменить"}
                    width={contentWidth * 0.42}
                    height={buttonHeight}
                    fontSize={20}
                    enabled={payingId == null && cancellingId == null}
                    onClick={() => void cancel(order.public_id)}
                  />
                </div>
              )}
            </div>
          ))
        )}
        {error && <p className="text-error">{error}</p>}
      </div>
    </ScreenLayout>
  );
}

function PaymentScreen({
  topHeight,
  buttonWidth,
  buttonHeight,
  result,
  publicId,
  onHome,
  onPaid,
  onFailed,
  cartCount,
}: {
  topHeight: number;
  buttonWidth: number;
  buttonHeight: number;
  result: "success" | "fail";
  publicId: string;
  onHome: () => void;
  onPaid: () => void;
  onFailed: () => void;
  cartCount: number;
}) {
  const [message, setMessage] = useState(result === "success" ? "Проверяем оплату..." : "Оплата не прошла");
  const [paymentStatus, setPaymentStatus] = useState<string | null>(result === "fail" ? "FAILED" : null);
  const [error, setError] = useState<string | null>(null);
  const finishedRef = useRef(false);

  useEffect(() => {
    if (result === "fail") {
      onFailed();
    }
  }, [result, onFailed]);

  useEffect(() => {
    if (result !== "success") return;
    let cancelled = false;
    let timer = 0;
    const poll = async () => {
      try {
        const status = await api.getPaymentStatus(publicId);
        if (cancelled || finishedRef.current) return;
        setPaymentStatus(status.payment_status);
        if (status.payment_status === "PAID") {
          setMessage(`Заказ ${publicId} оплачен и отправлен на кухню`);
          finishedRef.current = true;
          onPaid();
          return;
        }
        if (status.payment_status === "FAILED") {
          setMessage("Оплата не подтверждена");
          finishedRef.current = true;
          onFailed();
          return;
        }
        timer = window.setTimeout(poll, 2000);
      } catch (caught) {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : "Не удалось проверить оплату");
        }
      }
    };
    void poll();
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [publicId, result, onPaid, onFailed]);

  return (
    <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={onHome} onRight={onHome} cartCount={cartCount}>
      <div className="centered-column" style={{ width: buttonWidth }}>
        <h2>{result === "success" ? "Оплата" : "Оплата не прошла"}</h2>
        <p>{message}</p>
        {error && <p className="text-error">{error}</p>}
        {paymentStatus === "PAID" && <p className="text-muted">Переходим в «Мои заказы»...</p>}
      </div>
    </ScreenLayout>
  );
}

function ProfileScreen({
  profile,
  topHeight,
  buttonWidth,
  buttonHeight,
  onHome,
  onCart,
  onSaved,
  cartCount,
}: {
  profile: ClientProfile | null;
  topHeight: number;
  buttonWidth: number;
  buttonHeight: number;
  onHome: () => void;
  onCart: () => void;
  onSaved: (profile: ClientProfile) => void;
  cartCount: number;
}) {
  const [name, setName] = useState(profile?.name ?? "");
  const [phone, setPhone] = useState(profile?.phone ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fontSize, setFontSize] = useState(28);

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      const response = await api.updateProfile({ name: name || null, phone: phone || null });
      if (!profile) return;
      onSaved({
        ...profile,
        clientNumber: response.client_number,
        name: response.name,
        phone: response.phone,
        isBlocked: response.is_blocked,
        serverTimeOffsetMs: response.server_time - Date.now(),
      });
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось сохранить профиль");
    } finally {
      setSaving(false);
    }
  };

  return (
    <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={onHome} onRight={onCart} cartCount={cartCount}>
      <div className="centered-column" style={{ width: buttonWidth }}>
        <p>
          {profile?.clientNumber
            ? `Ваш внутренний номер № ${profile.clientNumber}`
            : "Ваш внутренний номер № загружается"}
        </p>
        <p className="text-muted" style={{ textAlign: "center" }}>
          Вы можете не указывать Ваши номер телефона и имя. Но если у нас будет вопрос по вашему заказу, мы не сможем с вами
          связаться и уточнить детали. В этом случае мы будем делать заказ по своим стандартам и претензии не принимаются.
        </p>
        <input className="field" placeholder="Имя" value={name} onChange={(event) => setName(event.target.value)} />
        <input className="field" placeholder="Номер телефона" value={phone} onChange={(event) => setPhone(event.target.value)} />
        {error && <p className="text-error">{error}</p>}
        <MenuButton
          text={saving ? "Сохраняем..." : "Сохранить"}
          width={buttonWidth}
          height={buttonHeight}
          fontSize={fontSize}
          onOverflow={() => setFontSize((value) => value * 0.9)}
          enabled={!saving}
          onClick={() => void save()}
        />
      </div>
    </ScreenLayout>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
