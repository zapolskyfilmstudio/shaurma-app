import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { api } from "./api";
import { loadSoundUrl, saveSound, type SoundKey } from "./audioStore";
import type {
  AdditionDto,
  CategoryDto,
  ClientDto,
  DayScheduleDto,
  KitchenOrderDto,
  MenuItemDto,
  OrderDto,
  OrderStatus,
  RemovalDto,
  SettingDto,
  StatisticsResponse,
} from "./types";
import {
  NEXT_STATUS_LABEL,
  STATUS_CHAIN,
  STATUS_LABEL,
  dayLabel,
  formatDateTime,
  formatMoney,
  moscowDateKey,
  moscowTodayKey,
  nextStatus,
  nullableText,
  parseNumber,
} from "./utils";

type TabId = "orders" | "stats" | "menu" | "clients" | "work";

const TABS: Array<{ id: TabId; title: string }> = [
  { id: "orders", title: "Заказы" },
  { id: "stats", title: "Статистика" },
  { id: "menu", title: "Меню" },
  { id: "clients", title: "Клиенты" },
  { id: "work", title: "Работа" },
];

type Cursor = {
  since_updated_at: number;
  since_id: number;
};

type SoundUrls = Record<SoundKey, string | null>;

const emptySoundUrls: SoundUrls = { new: null, alarm: null };

function App() {
  const [activeTab, setActiveTab] = useState<TabId>("orders");
  const [soundUrls, setSoundUrls] = useState<SoundUrls>(emptySoundUrls);

  const reloadSounds = useCallback(async () => {
    const [newUrl, alarmUrl] = await Promise.all([loadSoundUrl("new"), loadSoundUrl("alarm")]);
    setSoundUrls((previous) => {
      Object.values(previous).forEach((url) => {
        if (url) URL.revokeObjectURL(url);
      });
      return { new: newUrl, alarm: alarmUrl };
    });
  }, []);

  useEffect(() => {
    void reloadSounds();
    return () => {
      Object.values(soundUrls).forEach((url) => {
        if (url) URL.revokeObjectURL(url);
      });
    };
    // URLs are revoked when reloaded; this cleanup is only for unmount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reloadSounds]);

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <h1>Кухня</h1>
          <p>Рабочая станция повара</p>
        </div>
        <div className="config-note">
          Сервер: <code>{import.meta.env.VITE_API_URL || "http://localhost:8080"}</code>
        </div>
      </header>

      <nav className="tabs">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            className={activeTab === tab.id ? "active" : ""}
            onClick={() => setActiveTab(tab.id)}
            type="button"
          >
            {tab.title}
          </button>
        ))}
      </nav>

      <main>
        {activeTab === "orders" && <OrdersTab soundUrls={soundUrls} />}
        {activeTab === "stats" && <StatisticsTab />}
        {activeTab === "menu" && <MenuTab />}
        {activeTab === "clients" && <ClientsTab />}
        {activeTab === "work" && <WorkTab onSoundsChanged={reloadSounds} />}
      </main>
    </div>
  );
}

function OrdersTab({ soundUrls }: { soundUrls: SoundUrls }) {
  const [orders, setOrders] = useState<KitchenOrderDto[]>([]);
  const [cursor, setCursor] = useState<Cursor>({ since_updated_at: 0, since_id: 0 });
  const cursorRef = useRef(cursor);
  const [dayOffset, setDayOffset] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastPoll, setLastPoll] = useState<number | null>(null);
  const [updating, setUpdating] = useState<string | null>(null);
  const [soundBlocked, setSoundBlocked] = useState(false);
  const [now, setNow] = useState(Date.now());
  const seenNewRef = useRef(loadSeenNew());
  const newAudioRef = useRef<HTMLAudioElement | null>(null);
  const alarmAudioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    cursorRef.current = cursor;
  }, [cursor]);

  useEffect(() => {
    newAudioRef.current = soundUrls.new ? new Audio(soundUrls.new) : null;
  }, [soundUrls.new]);

  useEffect(() => {
    alarmAudioRef.current = soundUrls.alarm ? new Audio(soundUrls.alarm) : null;
    if (alarmAudioRef.current) alarmAudioRef.current.loop = true;
  }, [soundUrls.alarm]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const playNewSound = useCallback(() => {
    const audio = newAudioRef.current;
    if (!audio) return;
    audio.currentTime = 0;
    void audio.play().catch(() => setSoundBlocked(true));
  }, []);

  const mergeOrders = useCallback((incoming: KitchenOrderDto[]) => {
    if (incoming.length === 0) return;
    const newlySeen: string[] = [];
    setOrders((previous) => {
      const byPublicId = new Map(previous.map((order) => [order.public_id, order]));
      incoming.forEach((order) => {
        byPublicId.set(order.public_id, order);
        if (order.status === "NEW" && !seenNewRef.current.has(order.public_id)) {
          seenNewRef.current.add(order.public_id);
          newlySeen.push(order.public_id);
        }
      });
      return Array.from(byPublicId.values()).sort(sortByRequestedTime);
    });
    if (newlySeen.length > 0) {
      saveSeenNew(seenNewRef.current);
      playNewSound();
    }
  }, [playNewSound]);

  const pollOrders = useCallback(async () => {
    setLoading(true);
    try {
      const response = await api.getOrders(cursorRef.current.since_updated_at, cursorRef.current.since_id);
      mergeOrders(response.orders);
      const next = response.orders.reduce<Cursor>((maxCursor, order) => {
        if (
          order.updated_at > maxCursor.since_updated_at ||
          (order.updated_at === maxCursor.since_updated_at && order.id > maxCursor.since_id)
        ) {
          return { since_updated_at: order.updated_at, since_id: order.id };
        }
        return maxCursor;
      }, cursorRef.current);
      setCursor(next);
      setLastPoll(Date.now());
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось загрузить заказы");
    } finally {
      setLoading(false);
    }
  }, [mergeOrders]);

  useEffect(() => {
    void pollOrders();
    const timer = window.setInterval(() => void pollOrders(), 10_000);
    return () => window.clearInterval(timer);
  }, [pollOrders]);

  const alarmActive = useMemo(
    () => orders.some((order) => order.status === "NEW" && now >= order.cooking_start_time),
    [orders, now],
  );

  useEffect(() => {
    const audio = alarmAudioRef.current;
    if (!audio) return;
    if (alarmActive) {
      if (audio.paused) {
        void audio.play().catch(() => setSoundBlocked(true));
      }
    } else {
      audio.pause();
      audio.currentTime = 0;
    }
  }, [alarmActive]);

  const unlockSound = async () => {
    const audios = [newAudioRef.current, alarmAudioRef.current].filter(Boolean) as HTMLAudioElement[];
    for (const audio of audios) {
      try {
        audio.muted = true;
        await audio.play();
        audio.pause();
        audio.currentTime = 0;
        audio.muted = false;
      } catch {
        audio.muted = false;
      }
    }
    setSoundBlocked(false);
  };

  const selectedDayKey = moscowTodayKey(dayOffset);
  const visibleOrders = orders.filter(
    (order) => order.status !== "COMPLETED" && moscowDateKey(order.requested_time) === selectedDayKey,
  );

  const updateStatus = async (order: KitchenOrderDto, status: OrderStatus) => {
    setUpdating(order.public_id);
    try {
      const updated = await api.updateOrderStatus(order.public_id, status);
      setOrders((previous) =>
        previous.map((item) => (item.public_id === order.public_id ? { ...item, ...updated, client: item.client } : item)),
      );
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось обновить статус");
    } finally {
      setUpdating(null);
    }
  };

  return (
    <section className="panel">
      <div className="panel-header">
        <div>
          <h2>Заказы</h2>
          <p>
            Опрос каждые 10 секунд. Последнее обновление: {lastPoll ? formatDateTime(lastPoll) : "ещё не было"}.
          </p>
        </div>
        <button type="button" onClick={() => void pollOrders()} disabled={loading}>
          {loading ? "Обновляем..." : "Обновить"}
        </button>
      </div>

      {soundBlocked && (
        <div className="notice warning">
          Браузер заблокировал звук. Нажмите кнопку после любого действия на странице.
          <button type="button" onClick={() => void unlockSound()}>
            Включить звук
          </button>
        </div>
      )}
      {!soundUrls.new || !soundUrls.alarm ? (
        <div className="notice">MP3 для новых заказов и тревоги можно загрузить во вкладке «Работа».</div>
      ) : null}
      {error && <div className="notice error">{error}</div>}

      <div className="subtabs">
        {[0, 1, 2, 3].map((offset) => (
          <button
            key={offset}
            type="button"
            className={dayOffset === offset ? "active" : ""}
            onClick={() => setDayOffset(offset)}
          >
            {dayLabel(offset)}
          </button>
        ))}
      </div>

      <div className="orders-grid">
        {visibleOrders.length === 0 ? (
          <div className="empty">На выбранный день активных заказов нет.</div>
        ) : (
          visibleOrders.map((order) => (
            <OrderCard
              key={order.public_id}
              order={order}
              alarm={order.status === "NEW" && now >= order.cooking_start_time}
              updating={updating === order.public_id}
              onStatus={(status) => void updateStatus(order, status)}
            />
          ))
        )}
      </div>
    </section>
  );
}

function OrderCard({
  order,
  alarm,
  updating,
  onStatus,
}: {
  order: KitchenOrderDto | OrderDto;
  alarm?: boolean;
  updating?: boolean;
  onStatus?: (status: OrderStatus) => void;
}) {
  const target = nextStatus(order.status);
  const client = "client" in order ? order.client : null;

  return (
    <article className={`order-card status-${order.status.toLowerCase()} ${alarm ? "alarm" : ""}`}>
      <div className="order-top">
        <div className="public-id">{order.public_id}</div>
        <span className="badge">{STATUS_LABEL[order.status]}</span>
      </div>

      {client && (
        <div className="client-line">
          Клиент №{client.client_number} · {client.phone || "телефон не указан"} · {client.name || "имя не указано"}
        </div>
      )}

      <dl className="order-times">
        <div>
          <dt>Выдача</dt>
          <dd>{formatDateTime(order.requested_time)}</dd>
        </div>
        <div>
          <dt>Начать готовить</dt>
          <dd>{formatDateTime(order.cooking_start_time)}</dd>
        </div>
      </dl>

      <div className="items-list">
        {order.items.map((item) => (
          <div className="order-item" key={item.id}>
            <div>
              <strong>{item.name_snapshot}</strong>
              <span>
                {item.weight_snapshot} г · {formatMoney(item.price_snapshot)}
              </span>
            </div>
            {item.additions_snapshot.length > 0 && (
              <p>✅ {item.additions_snapshot.map((addition) => `${addition.name} +${formatMoney(addition.price)}`).join(", ")}</p>
            )}
            {item.removals_snapshot.length > 0 && (
              <p>НЕ КЛАСТЬ: {item.removals_snapshot.map((removal) => removal.name).join(", ")}</p>
            )}
          </div>
        ))}
      </div>

      {order.general_comment && <div className="comment">Комментарий: {order.general_comment}</div>}

      {order.delivery_enabled && (
        <div className="delivery-info">
          <strong>Доставка</strong>
          <p>Телефон: {order.delivery_phone || "не указан"}</p>
          <p>Адрес: {order.delivery_address || "не указан"}</p>
        </div>
      )}

      <div className="order-footer">
        <strong>Итого: {formatMoney(order.total_price)}</strong>
      </div>

      {onStatus && (
        <div className="status-actions">
          {STATUS_CHAIN.slice(1).map((status) => (
            <button
              key={status}
              type="button"
              disabled={updating || status !== target}
              onClick={() => onStatus(status)}
            >
              {status === target ? NEXT_STATUS_LABEL[order.status] : STATUS_LABEL[status]}
            </button>
          ))}
        </div>
      )}
    </article>
  );
}

function StatisticsTab() {
  const today = moscowTodayKey();
  const [from, setFrom] = useState(today);
  const [to, setTo] = useState(today);
  const [stats, setStats] = useState<StatisticsResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const loadStats = useCallback(async (fromDate = from, toDate = to) => {
    setLoading(true);
    try {
      setStats(await api.getStatistics(fromDate, toDate));
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось загрузить статистику");
    } finally {
      setLoading(false);
    }
  }, [from, to]);

  useEffect(() => {
    void loadStats();
  }, [loadStats]);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    void loadStats(from, to);
  };

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>Статистика</h2>
      </div>
      {error && <div className="notice error">{error}</div>}
      <form className="inline-form" onSubmit={submit}>
        <label>
          С
          <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        </label>
        <label>
          По
          <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
        </label>
        <button type="submit" disabled={loading}>
          Показать
        </button>
      </form>
      <div className="stats-grid">
        <StatsCard title="Сегодня" bucket={stats?.today} />
        <StatsCard title="Выбранный период" bucket={stats?.period} />
      </div>
    </section>
  );
}

function StatsCard({ title, bucket }: { title: string; bucket?: { total_sum: number; order_count: number } }) {
  return (
    <div className="stat-card">
      <h3>{title}</h3>
      <div className="stat-value">{formatMoney(bucket?.total_sum ?? 0)}</div>
      <p>{bucket?.order_count ?? 0} завершённых заказов</p>
    </div>
  );
}

function MenuTab() {
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [items, setItems] = useState<MenuItemDto[]>([]);
  const [additions, setAdditions] = useState<AdditionDto[]>([]);
  const [removals, setRemovals] = useState<RemovalDto[]>([]);
  const [editingCategory, setEditingCategory] = useState<CategoryDto | null>(null);
  const [editingItem, setEditingItem] = useState<MenuItemDto | null>(null);
  const [editingAddition, setEditingAddition] = useState<AdditionDto | null>(null);
  const [editingRemoval, setEditingRemoval] = useState<RemovalDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [categoriesData, itemsData, additionsData, removalsData] = await Promise.all([
        api.getCategories(),
        api.getMenuItems(),
        api.getAdditions(),
        api.getRemovals(),
      ]);
      setCategories(categoriesData);
      setItems(itemsData);
      setAdditions(additionsData);
      setRemovals(removalsData);
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось загрузить меню");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const run = async (action: () => Promise<unknown>) => {
    try {
      await action();
      await load();
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Операция не выполнена");
    }
  };

  const submitCategory = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body = {
      name: String(form.get("name") ?? ""),
      sort_order: parseNumber(form.get("sort_order")),
      is_active: form.get("is_active") === "on",
      is_grill: form.get("is_grill") === "on",
    };
    void run(async () => {
      if (editingCategory) await api.updateCategory(editingCategory.id, body);
      else await api.createCategory(body);
      setEditingCategory(null);
    });
  };

  const submitItem = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body = {
      category_id: parseNumber(form.get("category_id")),
      name: String(form.get("name") ?? ""),
      description: nullableText(form.get("description")),
      price: parseNumber(form.get("price")),
      weight: parseNumber(form.get("weight")),
      cooking_time: parseNumber(form.get("cooking_time")),
      image_url: nullableText(form.get("image_url")),
      sort_order: parseNumber(form.get("sort_order")),
      is_active: form.get("is_active") === "on",
    };
    void run(async () => {
      if (editingItem) await api.updateMenuItem(editingItem.id, body);
      else await api.createMenuItem(body);
      setEditingItem(null);
    });
  };

  const submitAddition = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body = {
      menu_item_id: parseNumber(form.get("menu_item_id")),
      name: String(form.get("name") ?? ""),
      price: parseNumber(form.get("price")),
      weight: parseNumber(form.get("weight")),
      is_active: form.get("is_active") === "on",
    };
    void run(async () => {
      if (editingAddition) await api.updateAddition(editingAddition.id, body);
      else await api.createAddition(body);
      setEditingAddition(null);
    });
  };

  const submitRemoval = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body = {
      menu_item_id: parseNumber(form.get("menu_item_id")),
      name: String(form.get("name") ?? ""),
      is_active: form.get("is_active") === "on",
    };
    void run(async () => {
      if (editingRemoval) await api.updateRemoval(editingRemoval.id, body);
      else await api.createRemoval(body);
      setEditingRemoval(null);
    });
  };

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>Меню</h2>
        <button type="button" onClick={() => void load()} disabled={loading}>
          Обновить
        </button>
      </div>
      {error && <div className="notice error">{error}</div>}

      <div className="admin-grid">
        <AdminBlock title="Категории">
          <CategoryForm key={editingCategory?.id ?? "new"} value={editingCategory} onSubmit={submitCategory} onCancel={() => setEditingCategory(null)} />
          <DataTable headers={["ИД", "Название", "Порядок", "Гриль", "Активна", "Действия"]}>
            {categories.map((category) => (
              <tr key={category.id}>
                <td>{category.id}</td>
                <td>{category.name}</td>
                <td>{category.sort_order}</td>
                <td>{category.is_grill ? "Да" : "Нет"}</td>
                <td>{category.is_active ? "Да" : "Нет"}</td>
                <td className="table-actions">
                  <button type="button" onClick={() => setEditingCategory(category)}>Править</button>
                  <button type="button" onClick={() => void run(() => api.deleteCategory(category.id))}>Скрыть</button>
                </td>
              </tr>
            ))}
          </DataTable>
        </AdminBlock>

        <AdminBlock title="Позиции меню">
          <MenuItemForm
            key={editingItem?.id ?? "new"}
            value={editingItem}
            categories={categories}
            onSubmit={submitItem}
            onCancel={() => setEditingItem(null)}
          />
          <DataTable headers={["ИД", "Категория", "Название", "Цена", "Вес", "Мин", "Активна", "Действия"]}>
            {items.map((item) => (
              <tr key={item.id}>
                <td>{item.id}</td>
                <td>{categoryName(categories, item.category_id)}</td>
                <td>{item.name}</td>
                <td>{formatMoney(item.price)}</td>
                <td>{item.weight} г</td>
                <td>{item.cooking_time}</td>
                <td>{item.is_active ? "Да" : "Нет"}</td>
                <td className="table-actions">
                  <button type="button" onClick={() => setEditingItem(item)}>Править</button>
                  <button type="button" onClick={() => void run(() => api.deleteMenuItem(item.id))}>Скрыть</button>
                </td>
              </tr>
            ))}
          </DataTable>
        </AdminBlock>

        <AdminBlock title="Дополнения">
          <AdditionForm
            key={editingAddition?.id ?? "new"}
            value={editingAddition}
            items={items}
            onSubmit={submitAddition}
            onCancel={() => setEditingAddition(null)}
          />
          <DataTable headers={["ИД", "Позиция", "Название", "Цена", "Вес", "Активно", "Действия"]}>
            {additions.map((addition) => (
              <tr key={addition.id}>
                <td>{addition.id}</td>
                <td>{itemName(items, addition.menu_item_id)}</td>
                <td>{addition.name}</td>
                <td>{formatMoney(addition.price)}</td>
                <td>{addition.weight} г</td>
                <td>{addition.is_active ? "Да" : "Нет"}</td>
                <td className="table-actions">
                  <button type="button" onClick={() => setEditingAddition(addition)}>Править</button>
                  <button type="button" onClick={() => void run(() => api.deleteAddition(addition.id))}>Скрыть</button>
                </td>
              </tr>
            ))}
          </DataTable>
        </AdminBlock>

        <AdminBlock title="Исключения">
          <RemovalForm
            key={editingRemoval?.id ?? "new"}
            value={editingRemoval}
            items={items}
            onSubmit={submitRemoval}
            onCancel={() => setEditingRemoval(null)}
          />
          <DataTable headers={["ИД", "Позиция", "Название", "Активно", "Действия"]}>
            {removals.map((removal) => (
              <tr key={removal.id}>
                <td>{removal.id}</td>
                <td>{itemName(items, removal.menu_item_id)}</td>
                <td>{removal.name}</td>
                <td>{removal.is_active ? "Да" : "Нет"}</td>
                <td className="table-actions">
                  <button type="button" onClick={() => setEditingRemoval(removal)}>Править</button>
                  <button type="button" onClick={() => void run(() => api.deleteRemoval(removal.id))}>Скрыть</button>
                </td>
              </tr>
            ))}
          </DataTable>
        </AdminBlock>
      </div>
    </section>
  );
}

function ClientsTab() {
  const [search, setSearch] = useState("");
  const [clients, setClients] = useState<ClientDto[]>([]);
  const [selected, setSelected] = useState<ClientDto | null>(null);
  const [orders, setOrders] = useState<OrderDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const loadClients = useCallback(async (query: string) => {
    setLoading(true);
    try {
      const response = await api.getClients(query);
      setClients(response.clients);
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось загрузить клиентов");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadClients("");
  }, [loadClients]);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    void loadClients(search);
  };

  const selectClient = async (client: ClientDto) => {
    setSelected(client);
    try {
      const response = await api.getClientOrders(client.client_number);
      setOrders(response.orders.sort(sortByRequestedTime));
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось загрузить историю клиента");
    }
  };

  const toggleClient = async (client: ClientDto) => {
    try {
      const updated = await api.updateClientStatus(client.device_id, !client.is_blocked);
      setClients((previous) => previous.map((item) => (item.device_id === updated.device_id ? updated : item)));
      if (selected?.device_id === updated.device_id) setSelected(updated);
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось изменить статус клиента");
    }
  };

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>Клиенты</h2>
      </div>
      {error && <div className="notice error">{error}</div>}
      <form className="inline-form" onSubmit={submit}>
        <label>
          Поиск
        <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="номер, телефон, имя или идентификатор устройства" />
        </label>
        <button type="submit" disabled={loading}>Найти</button>
      </form>

      <div className="split">
        <DataTable headers={["Номер", "Телефон", "Имя", "Блок", "Создан", "Действия"]}>
          {clients.map((client) => (
            <tr key={client.device_id} onClick={() => void selectClient(client)} className="clickable-row">
              <td>№{client.client_number}</td>
              <td>{client.phone || "—"}</td>
              <td>{client.name || "—"}</td>
              <td>{client.is_blocked ? "Да" : "Нет"}</td>
              <td>{formatDateTime(client.created_at)}</td>
              <td>
                <button
                  type="button"
                  onClick={(event) => {
                    event.stopPropagation();
                    void toggleClient(client);
                  }}
                >
                  {client.is_blocked ? "Разблокировать" : "Заблокировать"}
                </button>
              </td>
            </tr>
          ))}
        </DataTable>

        <aside className="history">
          <h3>История клиента</h3>
          {selected ? (
            <>
              <div className="client-card">
                <strong>№{selected.client_number}</strong>
                <span>{selected.phone || "телефон не указан"}</span>
                <span>{selected.name || "имя не указано"}</span>
                <span>{selected.is_blocked ? "Заблокирован" : "Активен"}</span>
              </div>
              <div className="history-list">
                {orders.length === 0 ? <div className="empty">Заказов нет.</div> : orders.map((order) => <OrderCard key={order.public_id} order={order} />)}
              </div>
            </>
          ) : (
            <div className="empty">Выберите клиента в таблице.</div>
          )}
        </aside>
      </div>
    </section>
  );
}

const WEEKDAY_LABELS = ["Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"];

function WorkTab({ onSoundsChanged }: { onSoundsChanged: () => Promise<void> }) {
  const [weeklySchedule, setWeeklySchedule] = useState<DayScheduleDto[]>([]);
  const [settingsRows, setSettingsRows] = useState<SettingDto[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await api.getSettings();
      setSettingsRows(response.settings);
      setWeeklySchedule([...response.weekly_schedule].sort((left, right) => left.day_of_week - right.day_of_week));
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось загрузить настройки");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLoading(true);
    try {
      const response = await api.updateSettings({ weekly_schedule: weeklySchedule });
      setSettingsRows(response.settings);
      setWeeklySchedule([...response.weekly_schedule].sort((left, right) => left.day_of_week - right.day_of_week));
      setMessage("Расписание сохранено");
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось сохранить расписание");
    } finally {
      setLoading(false);
    }
  };

  const uploadSound = async (key: SoundKey, file: File | null) => {
    if (!file) return;
    try {
      await saveSound(key, file);
      await onSoundsChanged();
      setMessage("MP3 сохранён локально в браузере");
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Не удалось сохранить MP3");
    }
  };

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>Работа</h2>
        <button type="button" onClick={() => void load()} disabled={loading}>Обновить</button>
      </div>
      {message && <div className="notice success">{message}</div>}
      {error && <div className="notice error">{error}</div>}

      <form className="settings-form" onSubmit={(event) => void submit(event)}>
        <h3>Расписание по дням недели</h3>
        <DataTable headers={["День", "Открытие", "Последний приём заказов"]}>
          {weeklySchedule.map((day) => (
            <tr key={day.day_of_week}>
              <td>{WEEKDAY_LABELS[day.day_of_week - 1] ?? day.day_of_week}</td>
              <td>
                <input
                  type="time"
                  value={day.open_time.slice(0, 5)}
                  onChange={(event) =>
                    setWeeklySchedule((previous) =>
                      previous.map((entry) =>
                        entry.day_of_week === day.day_of_week ? { ...entry, open_time: event.target.value } : entry,
                      ),
                    )
                  }
                />
              </td>
              <td>
                <input
                  type="time"
                  value={day.last_order_time.slice(0, 5)}
                  onChange={(event) =>
                    setWeeklySchedule((previous) =>
                      previous.map((entry) =>
                        entry.day_of_week === day.day_of_week
                          ? { ...entry, last_order_time: event.target.value }
                          : entry,
                      ),
                    )
                  }
                />
              </td>
            </tr>
          ))}
        </DataTable>
        <button type="submit" disabled={loading || weeklySchedule.length !== 7}>Сохранить расписание</button>
      </form>

      <div className="sound-box">
        <h3>Локальные MP3</h3>
        <p>Файлы сохраняются в локальном хранилище браузера и используются для оповещений.</p>
        <label>
          Короткий звук нового заказа в формате MP3
          <input type="file" accept="audio/mpeg,audio/mp3" onChange={(event) => void uploadSound("new", event.target.files?.[0] ?? null)} />
        </label>
        <label>
          Зацикленный звук тревоги в формате MP3
          <input type="file" accept="audio/mpeg,audio/mp3" onChange={(event) => void uploadSound("alarm", event.target.files?.[0] ?? null)} />
        </label>
      </div>

      <DataTable headers={["Ключ", "Значение", "Обновлено"]}>
        {settingsRows.map((setting) => (
          <tr key={setting.key}>
            <td>{settingLabel(setting.key)}</td>
            <td>{setting.value}</td>
            <td>{formatDateTime(setting.updated_at)}</td>
          </tr>
        ))}
      </DataTable>
    </section>
  );
}

function AdminBlock({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="admin-block">
      <h3>{title}</h3>
      {children}
    </section>
  );
}

function DataTable({ headers, children }: { headers: string[]; children: ReactNode }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {headers.map((header) => (
              <th key={header}>{header}</th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

function CategoryForm({
  value,
  onSubmit,
  onCancel,
}: {
  value: CategoryDto | null;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
}) {
  return (
    <form className="crud-form" onSubmit={onSubmit}>
      <input name="name" placeholder="Название" defaultValue={value?.name ?? ""} required />
      <input name="sort_order" type="number" placeholder="Порядок" defaultValue={value?.sort_order ?? 0} />
      <label><input name="is_grill" type="checkbox" defaultChecked={value?.is_grill ?? false} /> Гриль</label>
      <label><input name="is_active" type="checkbox" defaultChecked={value?.is_active ?? true} /> Активна</label>
      <button type="submit">{value ? "Сохранить" : "Создать"}</button>
      {value && <button type="button" onClick={onCancel}>Отмена</button>}
    </form>
  );
}

function MenuItemForm({
  value,
  categories,
  onSubmit,
  onCancel,
}: {
  value: MenuItemDto | null;
  categories: CategoryDto[];
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
}) {
  return (
    <form className="crud-form" onSubmit={onSubmit}>
      <select name="category_id" defaultValue={value?.category_id ?? categories[0]?.id ?? ""} required>
        <option value="" disabled>Категория</option>
        {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
      </select>
      <input name="name" placeholder="Название" defaultValue={value?.name ?? ""} required />
      <input name="description" placeholder="Описание" defaultValue={value?.description ?? ""} />
      <input name="price" type="number" placeholder="Цена" defaultValue={value?.price ?? 0} min={0} />
      <input name="weight" type="number" placeholder="Вес" defaultValue={value?.weight ?? 0} min={0} />
      <input name="cooking_time" type="number" placeholder="Минуты" defaultValue={value?.cooking_time ?? 0} min={0} />
      <input name="image_url" placeholder="Ссылка на картинку" defaultValue={value?.image_url ?? ""} />
      <input name="sort_order" type="number" placeholder="Порядок" defaultValue={value?.sort_order ?? 0} />
      <label><input name="is_active" type="checkbox" defaultChecked={value?.is_active ?? true} /> Активна</label>
      <button type="submit">{value ? "Сохранить" : "Создать"}</button>
      {value && <button type="button" onClick={onCancel}>Отмена</button>}
    </form>
  );
}

function AdditionForm({
  value,
  items,
  onSubmit,
  onCancel,
}: {
  value: AdditionDto | null;
  items: MenuItemDto[];
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
}) {
  return (
    <form className="crud-form" onSubmit={onSubmit}>
      <select name="menu_item_id" defaultValue={value?.menu_item_id ?? items[0]?.id ?? ""} required>
        <option value="" disabled>Позиция</option>
        {items.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
      </select>
      <input name="name" placeholder="Название" defaultValue={value?.name ?? ""} required />
      <input name="price" type="number" placeholder="Цена" defaultValue={value?.price ?? 0} min={0} />
      <input name="weight" type="number" placeholder="Вес" defaultValue={value?.weight ?? 0} min={0} />
      <label><input name="is_active" type="checkbox" defaultChecked={value?.is_active ?? true} /> Активно</label>
      <button type="submit">{value ? "Сохранить" : "Создать"}</button>
      {value && <button type="button" onClick={onCancel}>Отмена</button>}
    </form>
  );
}

function RemovalForm({
  value,
  items,
  onSubmit,
  onCancel,
}: {
  value: RemovalDto | null;
  items: MenuItemDto[];
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
}) {
  return (
    <form className="crud-form" onSubmit={onSubmit}>
      <select name="menu_item_id" defaultValue={value?.menu_item_id ?? items[0]?.id ?? ""} required>
        <option value="" disabled>Позиция</option>
        {items.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
      </select>
      <input name="name" placeholder="Название" defaultValue={value?.name ?? ""} required />
      <label><input name="is_active" type="checkbox" defaultChecked={value?.is_active ?? true} /> Активно</label>
      <button type="submit">{value ? "Сохранить" : "Создать"}</button>
      {value && <button type="button" onClick={onCancel}>Отмена</button>}
    </form>
  );
}

function categoryName(categories: CategoryDto[], id: number): string {
  return categories.find((category) => category.id === id)?.name ?? `#${id}`;
}

function itemName(items: MenuItemDto[], id: number): string {
  return items.find((item) => item.id === id)?.name ?? `#${id}`;
}

function settingLabel(key: string): string {
  const labels: Record<string, string> = {
    work_start_time: "Начало работы",
    cutoff_regular: "Стоп обычных заказов",
    cutoff_grill: "Стоп гриля",
  };
  return labels[key] ?? key;
}

function sortByRequestedTime<T extends { requested_time: number; id: number }>(left: T, right: T): number {
  return left.requested_time - right.requested_time || left.id - right.id;
}

function loadSeenNew(): Set<string> {
  try {
    const parsed = JSON.parse(localStorage.getItem("seen_new") || "[]") as string[];
    return new Set(Array.isArray(parsed) ? parsed : []);
  } catch {
    return new Set();
  }
}

function saveSeenNew(value: Set<string>): void {
  localStorage.setItem("seen_new", JSON.stringify(Array.from(value).slice(-1000)));
}

export default App;
