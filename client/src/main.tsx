import { StrictMode, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { api } from "./api";
import { MenuButton, ScreenLayout, WheelPicker } from "./components";
import { clearCart, getDeviceId, loadCart, loadProfile, saveCart, saveProfile } from "./storage";
import {
  allowedDates,
  allowedHourRange,
  allowedMinuteRange,
  formatDateTime,
  formatMoney,
  isCafeOpen,
  maximumRequestedTime,
  minimumRequestedTime,
  monthNameRu,
  moscowToMs,
  normalizeMenuName,
  parseTimeToMinutes,
  toMoscowParts,
} from "./timeRules";
import type {
  CartItem,
  CategoryDto,
  ClientProfile,
  CreateOrderResponse,
  MenuItemDto,
  OrderDto,
} from "./types";
import "./styles.css";

const PENDING_ORDER_KEY = "pending_order_public_id";

type Route =
  | { name: "startup" }
  | { name: "blocked" }
  | { name: "main" }
  | { name: "missing"; title: string }
  | { name: "category"; categoryId: number }
  | { name: "product"; itemId: number }
  | { name: "cart" }
  | { name: "orders" }
  | { name: "profile" }
  | { name: "checkout"; publicId: string; paymentUrl: string; totalPrice: number }
  | { name: "payment"; result: "success" | "fail"; publicId: string };

const MENU_BUTTONS = ["ШАУРМА", "ГРИЛЬ НА УГЛЯХ", "КАРТОШКА & СНЕКИ", "НАПИТКИ", "МОИ ЗАКАЗЫ"];

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
  const [message, setMessage] = useState("Загружаем...");
  const [profile, setProfile] = useState<ClientProfile | null>(loadProfile());
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [cart, setCart] = useState<CartItem[]>(loadCart());
  const [orders, setOrders] = useState<OrderDto[]>([]);
  const [workStart, setWorkStart] = useState("00:00");
  const [cutoff, setCutoff] = useState("23:00");
  const [isOpen, setIsOpen] = useState(true);
  const [paymentEnabled, setPaymentEnabled] = useState(false);
  const [serverOffset, setServerOffset] = useState(profile?.serverTimeOffsetMs ?? 0);

  const serverNow = () => Date.now() + serverOffset;

  const persistCart = useCallback((items: CartItem[]) => {
    setCart(items);
    saveCart(items);
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
    setWorkStart(config.work_start_time);
    setCutoff(config.cutoff_time);
    setIsOpen(config.is_open);
    setPaymentEnabled(config.payment_enabled);
    setCategories(menu.categories);
    if (init.is_blocked) {
      setRoute({ name: "blocked" });
      return;
    }
    const params = new URLSearchParams(window.location.search);
    const paymentResult = params.get("payment");
    const pendingOrder = sessionStorage.getItem(PENDING_ORDER_KEY);
    if ((paymentResult === "success" || paymentResult === "fail") && pendingOrder) {
      window.history.replaceState({}, "", window.location.pathname);
      setRoute({ name: "payment", result: paymentResult, publicId: pendingOrder });
      return;
    }
    setRoute({ name: "main" });
  }, []);

  useEffect(() => {
    void bootstrap().catch((error) => setMessage(error instanceof Error ? error.message : "Ошибка загрузки"));
  }, [bootstrap]);

  useEffect(() => {
    if (route.name !== "orders") return;
    const load = () => {
      void api
        .myOrders()
        .then((response) => setOrders(response.orders))
        .catch(() => undefined);
    };
    load();
    const timer = window.setInterval(load, 10_000);
    return () => window.clearInterval(timer);
  }, [route.name]);

  const findCategoryByTitle = (title: string) =>
    categories.find((category) => normalizeMenuName(category.name) === normalizeMenuName(title));

  const allItems = useMemo(
    () => categories.flatMap((category) => category.items.map((item) => ({ ...item, category }))),
    [categories],
  );

  const goMain = () => setRoute({ name: "main" });
  const goCart = () => setRoute({ name: "cart" });
  const goProfile = () => setRoute({ name: "profile" });

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
        onCategory={(categoryId) => setRoute({ name: "category", categoryId })}
        onMissing={(title) => setRoute({ name: "missing", title })}
        onOrders={() => setRoute({ name: "orders" })}
        findCategoryByTitle={findCategoryByTitle}
      />
    );
  }

  if (route.name === "missing") {
    return (
      <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={goMain} onRight={goCart}>
        <div className="centered-column">
          <h2>{route.title}</h2>
          <p className="text-muted">Раздел скоро появится</p>
        </div>
      </ScreenLayout>
    );
  }

  if (route.name === "category") {
    const category = categories.find((item) => item.id === route.categoryId);
    const items = category?.items.filter((item) => item.is_active) ?? [];
    return (
      <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={goMain} onRight={goCart}>
        <div className="category-screen">
          <h2>{category?.name ?? "Категория"}</h2>
          {items.map((item) => (
            <button
              key={item.id}
              type="button"
              className="card"
              onClick={() => setRoute({ name: "product", itemId: item.id })}
            >
              <strong>{item.name}</strong>
              {item.description && <p className="text-muted">{item.description}</p>}
              <p>
                {formatMoney(item.price)} · {item.weight} г · {item.cooking_time} мин
              </p>
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
        buttonWidth={buttonWidth}
        buttonHeight={buttonHeight}
        onBack={() => {
          if (productItem) setRoute({ name: "category", categoryId: productItem.category_id });
          else goMain();
        }}
        onAdded={(item) => {
          persistCart([...cart, item]);
          if (productItem) setRoute({ name: "category", categoryId: productItem.category_id });
          else goMain();
        }}
        goMain={goMain}
        goCart={goCart}
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
        workStart={workStart}
        cutoff={cutoff}
        isOpen={isOpen}
        onHome={goMain}
        onProfile={goProfile}
        onEdit={(menuItemId) => {
          persistCart(cart.filter((entry) => entry.menuItemId !== menuItemId));
          setRoute({ name: "product", itemId: menuItemId });
        }}
        onCartChange={persistCart}
        onOrdered={(result) => {
          const orderTotal = cart.reduce((sum, item) => sum + item.totalPrice, 0);
          if (result.payment_url && result.payment_status === "WAITING") {
            sessionStorage.setItem(PENDING_ORDER_KEY, result.public_id);
            clearCart();
            setCart([]);
            setRoute({
              name: "checkout",
              publicId: result.public_id,
              paymentUrl: result.payment_url,
              totalPrice: orderTotal,
            });
            return;
          }
          if (result.payment_status === "PAID") {
            clearCart();
            setCart([]);
            setRoute({ name: "orders" });
            return;
          }
          setRoute({ name: "cart" });
        }}
        paymentEnabled={paymentEnabled}
      />
    );
  }

  if (route.name === "checkout") {
    return (
      <CheckoutScreen
        topHeight={topHeight}
        buttonWidth={buttonWidth}
        buttonHeight={buttonHeight}
        publicId={route.publicId}
        paymentUrl={route.paymentUrl}
        totalPrice={route.totalPrice}
        onHome={goMain}
        onPaid={() => {
          sessionStorage.removeItem(PENDING_ORDER_KEY);
          setRoute({ name: "orders" });
        }}
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
        onOrders={() => {
          sessionStorage.removeItem(PENDING_ORDER_KEY);
          setRoute({ name: "orders" });
        }}
        onRetryCheckout={(checkoutPublicId, checkoutPaymentUrl, checkoutTotalPrice) => {
          setRoute({
            name: "checkout",
            publicId: checkoutPublicId,
            paymentUrl: checkoutPaymentUrl,
            totalPrice: checkoutTotalPrice,
          });
        }}
      />
    );
  }

  if (route.name === "orders") {
    return (
      <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={goMain} onRight={goCart}>
        <div className="list-screen">
          {orders.length === 0 ? (
            <p>Заказов пока нет</p>
          ) : (
            orders.map((order) => (
              <div key={order.public_id} className="card">
                <p>Дата и время: {formatDateTime(order.created_at)}</p>
                {order.items.map((item) => (
                  <p key={item.id}>
                    {item.name_snapshot} — {formatMoney(item.price_snapshot)}
                  </p>
                ))}
                <p>Итоговая цена: {formatMoney(order.total_price)}</p>
              </div>
            ))
          )}
        </div>
      </ScreenLayout>
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
}) {
  const [fontSize, setFontSize] = useState(28);
  return (
    <ScreenLayout topHeight={topHeight} left="profile" right="cart" onLeft={onProfile} onRight={onCart}>
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
              const category = findCategoryByTitle(title);
              if (!category) onMissing(title);
              else onCategory(category.id);
            }}
          />
        ))}
      </div>
    </ScreenLayout>
  );
}

function ProductScreen({
  item,
  topHeight,
  buttonWidth,
  buttonHeight,
  onBack,
  onAdded,
  goMain,
  goCart,
}: {
  item: (MenuItemDto & { category: CategoryDto }) | null;
  topHeight: number;
  buttonWidth: number;
  buttonHeight: number;
  onBack: () => void;
  onAdded: (item: CartItem) => void;
  goMain: () => void;
  goCart: () => void;
}) {
  const resolved = item;
  const [selectedAdditions, setSelectedAdditions] = useState<number[]>([]);
  const [selectedRemovals, setSelectedRemovals] = useState<number[]>([]);
  const [showOptions, setShowOptions] = useState(false);
  const [adding, setAdding] = useState(false);

  if (!resolved) {
    return (
      <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={goMain} onRight={goCart}>
        <div className="centered-column">
          <div className="spinner" />
          <p>Загружаем товар...</p>
        </div>
      </ScreenLayout>
    );
  }

  const additionsPrice = resolved.additions
    .filter((addition) => selectedAdditions.includes(addition.id))
    .reduce((sum, addition) => sum + addition.price, 0);
  const totalPrice = resolved.price + additionsPrice;

  const addToCart = () => {
    setAdding(true);
    const cartItem: CartItem = {
      id: crypto.randomUUID(),
      menuItemId: resolved.id,
      name: resolved.name,
      price: resolved.price,
      weight: resolved.weight,
      cookingTime: resolved.cooking_time,
      additionsIds: selectedAdditions,
      removalsIds: selectedRemovals,
      additionNames: resolved.additions
        .filter((addition) => selectedAdditions.includes(addition.id))
        .map((addition) => addition.name)
        .join(", "),
      removalNames: resolved.removals
        .filter((removal) => selectedRemovals.includes(removal.id))
        .map((removal) => removal.name)
        .join(", "),
      totalPrice,
    };
    onAdded(cartItem);
    setAdding(false);
  };

  return (
    <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={goMain} onRight={goCart}>
      <div className="product-screen">
        <button type="button" className="menu-btn" style={{ width: 120, height: 40 }} onClick={onBack}>
          НАЗАД
        </button>
        <h2>{resolved.name}</h2>
        {resolved.description && <p className="text-muted">{resolved.description}</p>}
        <p>
          {resolved.weight} г · готовится {resolved.cooking_time} мин
        </p>
        <p>Цена: {formatMoney(totalPrice)}</p>
        <MenuButton text="Дополнительно / убрать" width={buttonWidth} height={buttonHeight} fontSize={18} onClick={() => setShowOptions(true)} />
        <MenuButton
          text={adding ? "Добавляем..." : "Добавить в корзину"}
          width={buttonWidth}
          height={buttonHeight}
          fontSize={20}
          enabled={!adding}
          onClick={addToCart}
        />
      </div>
      {showOptions && (
        <div className="modal-backdrop" onClick={() => setShowOptions(false)}>
          <div className="modal" onClick={(event) => event.stopPropagation()}>
            <strong>Добавить</strong>
            {resolved.additions.map((addition) => (
              <label key={addition.id} className="check-row">
                <span>
                  {addition.name} +{formatMoney(addition.price)}
                </span>
                <input
                  type="checkbox"
                  checked={selectedAdditions.includes(addition.id)}
                  onChange={() =>
                    setSelectedAdditions((current) =>
                      current.includes(addition.id) ? current.filter((id) => id !== addition.id) : [...current, addition.id],
                    )
                  }
                />
              </label>
            ))}
            <strong>Не класть</strong>
            {resolved.removals.map((removal) => (
              <label key={removal.id} className="check-row">
                <span>{removal.name}</span>
                <input
                  type="checkbox"
                  checked={selectedRemovals.includes(removal.id)}
                  onChange={() =>
                    setSelectedRemovals((current) =>
                      current.includes(removal.id) ? current.filter((id) => id !== removal.id) : [...current, removal.id],
                    )
                  }
                />
              </label>
            ))}
            <MenuButton text="Готово" width={buttonWidth * 0.6} height={44} fontSize={18} onClick={() => setShowOptions(false)} />
          </div>
        </div>
      )}
    </ScreenLayout>
  );
}

function CartScreen({
  cart,
  topHeight,
  viewport,
  serverNow,
  workStart,
  cutoff,
  isOpen,
  onHome,
  onProfile,
  onEdit,
  onCartChange,
  onOrdered,
  paymentEnabled,
}: {
  cart: CartItem[];
  topHeight: number;
  viewport: { width: number; height: number };
  serverNow: () => number;
  workStart: string;
  cutoff: string;
  isOpen: boolean;
  onHome: () => void;
  onProfile: () => void;
  onEdit: (menuItemId: number) => void;
  onCartChange: (items: CartItem[]) => void;
  onOrdered: (result: CreateOrderResponse) => void;
  paymentEnabled: boolean;
}) {
  const contentWidth = viewport.width * 0.9;
  const buttonWidth = viewport.width * 0.8;
  const buttonHeight = viewport.height * 0.85 * 0.075;
  const wheelHeight = viewport.height * 0.85 * 0.11;
  const [comment, setComment] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [fontSize, setFontSize] = useState(22);
  const userPickedTime = useRef(false);
  const maxCooking = cart.length ? Math.max(...cart.map((item) => item.cookingTime)) : 0;
  const minTime = minimumRequestedTime(serverNow(), maxCooking);
  const maxTime = maximumRequestedTime(serverNow());
  const [requestedTime, setRequestedTime] = useState(minTime);

  useEffect(() => {
    const sync = () => {
      void api.config().then((config) => {
        const now = config.server_time;
        const nextMin = minimumRequestedTime(now, maxCooking);
        if (!userPickedTime.current || requestedTime < nextMin) {
          setRequestedTime(nextMin);
        }
      });
    };
    sync();
    const timer = window.setInterval(sync, 8000);
    return () => window.clearInterval(timer);
  }, [maxCooking, requestedTime]);

  const selected = toMoscowParts(requestedTime);
  const dates = allowedDates(minTime, maxTime);
  const years = [...new Set(dates.map((date) => date.year))];
  const months = dates.filter((date) => date.year === selected.year).map((date) => date.month);
  const days = dates
    .filter((date) => date.year === selected.year && date.month === selected.month)
    .map((date) => date.day);
  const hourRange = allowedHourRange(selected, minTime, maxTime);
  const minuteRange = allowedMinuteRange(selected, selected.hour, minTime, maxTime);
  const isTimeValid =
    requestedTime >= minTime &&
    requestedTime <= maxTime &&
    selected.hour * 60 + selected.minute <= parseTimeToMinutes(cutoff) &&
    selected.hour * 60 + selected.minute >= parseTimeToMinutes(workStart);
  const hasDifferentCookingTimes = new Set(cart.map((item) => item.cookingTime)).size > 1;
  const cafeOpen = isOpen && isCafeOpen(serverNow(), workStart, cutoff);

  const setDate = (year: number, month: number, day: number) => {
    userPickedTime.current = true;
    const current = toMoscowParts(requestedTime);
    setRequestedTime(moscowToMs(year, month, day, current.hour, current.minute));
  };

  const setHour = (hour: number) => {
    userPickedTime.current = true;
    const current = toMoscowParts(requestedTime);
    const minutes = allowedMinuteRange(
      { year: current.year, month: current.month, day: current.day },
      hour,
      minTime,
      maxTime,
    );
    const minute = minutes.includes(current.minute) ? current.minute : minutes[0];
    setRequestedTime(moscowToMs(current.year, current.month, current.day, hour, minute));
  };

  const setMinute = (minute: number) => {
    userPickedTime.current = true;
    const current = toMoscowParts(requestedTime);
    setRequestedTime(moscowToMs(current.year, current.month, current.day, current.hour, minute));
  };

  const submit = async () => {
    if (!cafeOpen) return;
    setSubmitting(true);
    setError(null);
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
      if (result.payment_url || result.payment_status === "PAID") {
        onOrdered(result);
        return;
      }
      setError("Не удалось получить ссылку на оплату. Попробуйте позже.");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось создать заказ");
    } finally {
      setSubmitting(false);
    }
  };

  if (!cafeOpen) {
    return (
      <ScreenLayout topHeight={topHeight} left="home" right="profile" onLeft={onHome} onRight={onProfile}>
        <div className="closed-banner">Кафе закрыто, заказы с {workStart}</div>
      </ScreenLayout>
    );
  }

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
        {cart.map((item) => (
          <div key={item.id} className="card" style={{ width: contentWidth }}>
            <strong>{item.name}</strong>
            {item.additionNames && <p>Добавки: {item.additionNames}</p>}
            {item.removalNames && <p>Не класть: {item.removalNames}</p>}
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
        ))}
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
            selectedIndex={Math.max(0, hourRange.indexOf(selected.hour))}
            width={contentWidth * 0.28}
            height={wheelHeight}
            onChange={(index) => setHour(hourRange[index])}
          />
          <span className="wheel-separator">:</span>
          <WheelPicker
            key={`minute-${selected.hour}-${minuteRange.join(",")}`}
            values={minuteRange.map((minute) => String(minute).padStart(2, "0"))}
            selectedIndex={Math.max(0, minuteRange.indexOf(selected.minute))}
            width={contentWidth * 0.28}
            height={wheelHeight}
            onChange={(index) => setMinute(minuteRange[index])}
          />
        </div>
        <div className="wheel-row" style={{ width: contentWidth }}>
          <WheelPicker
            values={days.map((day) => String(day).padStart(2, "0"))}
            selectedIndex={Math.max(0, days.indexOf(selected.day))}
            width={contentWidth * 0.25}
            height={wheelHeight}
            onChange={(index) => setDate(selected.year, selected.month, days[index])}
          />
          <WheelPicker
            values={months.map((month) => monthNameRu(month))}
            selectedIndex={Math.max(0, months.indexOf(selected.month))}
            width={contentWidth * 0.35}
            height={wheelHeight}
            onChange={(index) => setDate(selected.year, months[index], selected.day)}
          />
          <WheelPicker
            values={years.map(String)}
            selectedIndex={Math.max(0, years.indexOf(selected.year))}
            width={contentWidth * 0.25}
            height={wheelHeight}
            onChange={(index) => setDate(years[index], selected.month, selected.day)}
          />
        </div>
        <p className="text-muted" style={{ width: contentWidth, textAlign: "center" }}>
          Минимум: {formatDateTime(minTime)}
        </p>
        {!isTimeValid && (
          <p className="text-error" style={{ width: contentWidth, textAlign: "center" }}>
            Выберите время не раньше минимального и не позже {cutoff} в пределах трёх дней.
          </p>
        )}
        {hasDifferentCookingTimes && (
          <p className="text-muted" style={{ width: contentWidth }}>
            Ваш заказ будет готов через {maxCooking} минут. Если вы хотите получить часть заказа раньше, оформите два заказа отдельно.
          </p>
        )}
        {error && <p className="text-error">{error}</p>}
        {!paymentEnabled && (
          <p className="text-error" style={{ width: contentWidth, textAlign: "center" }}>
            Онлайн-оплата временно недоступна — администратор должен настроить T-Bank на сервере.
          </p>
        )}
        <MenuButton text="Добавить к заказу" width={buttonWidth} height={buttonHeight} fontSize={fontSize} onClick={onHome} />
        <MenuButton
          text={submitting ? "Переходим к оплате..." : "Оплатить"}
          width={buttonWidth}
          height={buttonHeight}
          fontSize={fontSize}
          enabled={isTimeValid && !submitting && paymentEnabled}
          onClick={() => void submit()}
        />
      </div>
    </ScreenLayout>
  );
}

function CheckoutScreen({
  topHeight,
  buttonWidth,
  buttonHeight,
  publicId,
  paymentUrl,
  totalPrice,
  onHome,
  onPaid,
}: {
  topHeight: number;
  buttonWidth: number;
  buttonHeight: number;
  publicId: string;
  paymentUrl: string;
  totalPrice: number;
  onHome: () => void;
  onPaid: () => void;
}) {
  const [qrSrc, setQrSrc] = useState<string | null>(null);
  const [loadingQr, setLoadingQr] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState("Отсканируйте QR-код в приложении банка");

  useEffect(() => {
    let cancelled = false;
    const loadQr = async () => {
      setLoadingQr(true);
      setError(null);
      try {
        const response = await api.getSbpQr(publicId);
        if (cancelled) return;
        setQrSrc(`data:image/svg+xml;base64,${response.qr_svg_base64}`);
      } catch (caught) {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : "Не удалось получить QR для СБП");
        }
      } finally {
        if (!cancelled) setLoadingQr(false);
      }
    };
    void loadQr();
    return () => {
      cancelled = true;
    };
  }, [publicId]);

  useEffect(() => {
    let cancelled = false;
    const poll = async () => {
      try {
        const status = await api.getPaymentStatus(publicId);
        if (cancelled) return;
        if (status.payment_status === "PAID") {
          setMessage(`Заказ ${publicId} оплачен и отправлен на кухню`);
          window.setTimeout(onPaid, 800);
          return;
        }
        if (status.payment_status === "FAILED") {
          setMessage("Оплата не подтверждена");
          return;
        }
        window.setTimeout(poll, 2000);
      } catch {
        if (!cancelled) window.setTimeout(poll, 3000);
      }
    };
    void poll();
    return () => {
      cancelled = true;
    };
  }, [publicId, onPaid]);

  return (
    <ScreenLayout topHeight={topHeight} left="home" right="home" onLeft={onHome} onRight={onHome}>
      <div className="payment-screen">
        <h2>Оплата заказа {publicId}</h2>
        <p className="payment-amount">{formatMoney(totalPrice)}</p>

        <section className="payment-section">
          <h3>1. СБП</h3>
          <p>{message}</p>
          {loadingQr && <div className="spinner" />}
          {qrSrc && (
            <img src={qrSrc} alt="QR-код для оплаты через СБП" className="sbp-qr" />
          )}
          {error && <p className="text-error">{error}</p>}
        </section>

        <section className="payment-section">
          <h3>2. Банковская карта</h3>
          <MenuButton
            text="Оплатить картой"
            width={buttonWidth}
            height={buttonHeight}
            fontSize={22}
            onClick={() => {
              window.location.href = paymentUrl;
            }}
          />
        </section>
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
  onOrders,
  onRetryCheckout,
}: {
  topHeight: number;
  buttonWidth: number;
  buttonHeight: number;
  result: "success" | "fail";
  publicId: string;
  onHome: () => void;
  onOrders: () => void;
  onRetryCheckout: (publicId: string, paymentUrl: string, totalPrice: number) => void;
}) {
  const [message, setMessage] = useState(result === "success" ? "Проверяем оплату..." : "Оплата не прошла");
  const [paymentStatus, setPaymentStatus] = useState<string | null>(null);
  const [retrying, setRetrying] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (result !== "success") return;
    let cancelled = false;
    const poll = async () => {
      try {
        const status = await api.getPaymentStatus(publicId);
        if (cancelled) return;
        setPaymentStatus(status.payment_status);
        if (status.payment_status === "PAID") {
          sessionStorage.removeItem(PENDING_ORDER_KEY);
          setMessage(`Заказ ${publicId} оплачен и отправлен на кухню`);
          return;
        }
        if (status.payment_status === "FAILED") {
          setMessage("Оплата не подтверждена");
          return;
        }
        window.setTimeout(poll, 2000);
      } catch (caught) {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : "Не удалось проверить оплату");
        }
      }
    };
    void poll();
    return () => {
      cancelled = true;
    };
  }, [publicId, result]);

  const retry = async () => {
    setRetrying(true);
    setError(null);
    try {
      const response = await api.retryPayment(publicId);
      if (response.payment_url && response.payment_status === "WAITING") {
        sessionStorage.setItem(PENDING_ORDER_KEY, publicId);
        const status = await api.getPaymentStatus(publicId);
        onRetryCheckout(publicId, response.payment_url, status.total_price ?? 0);
        return;
      }
      setMessage(`Заказ ${publicId} оплачен`);
      setPaymentStatus("PAID");
      sessionStorage.removeItem(PENDING_ORDER_KEY);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось повторить оплату");
    } finally {
      setRetrying(false);
    }
  };

  return (
    <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={onHome} onRight={onOrders}>
      <div className="centered-column" style={{ width: buttonWidth }}>
        <h2>{result === "success" ? "Оплата" : "Оплата не прошла"}</h2>
        <p>{message}</p>
        {paymentStatus === "PAID" && (
          <MenuButton text="Мои заказы" width={buttonWidth} height={buttonHeight} fontSize={24} onClick={onOrders} />
        )}
        {(result === "fail" || paymentStatus === "FAILED") && (
          <MenuButton
            text={retrying ? "Открываем оплату..." : "Попробовать снова"}
            width={buttonWidth}
            height={buttonHeight}
            fontSize={24}
            enabled={!retrying}
            onClick={() => void retry()}
          />
        )}
        {error && <p className="text-error">{error}</p>}
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
}: {
  profile: ClientProfile | null;
  topHeight: number;
  buttonWidth: number;
  buttonHeight: number;
  onHome: () => void;
  onCart: () => void;
  onSaved: (profile: ClientProfile) => void;
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
    <ScreenLayout topHeight={topHeight} left="home" right="cart" onLeft={onHome} onRight={onCart}>
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
