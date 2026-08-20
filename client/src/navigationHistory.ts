export type AppRoute =
  | { name: "startup" }
  | { name: "blocked" }
  | { name: "main" }
  | { name: "missing"; title: string }
  | { name: "category"; categoryId: number }
  | { name: "product"; itemId: number }
  | { name: "cart" }
  | { name: "orders" }
  | { name: "profile" }
  | { name: "checkout"; publicId: string; totalPrice: number }
  | { name: "sbpBanks"; publicId: string; totalPrice: number }
  | { name: "payment"; result: "success" | "fail"; publicId: string };

type HistoryState = {
  appRoute?: string;
};

const MAIN_HASH = "#/";
const MAIN_KEY = "main";

export function serializeRoute(route: AppRoute): string {
  switch (route.name) {
    case "category":
      return `category:${route.categoryId}`;
    case "product":
      return `product:${route.itemId}`;
    case "missing":
      return `missing:${route.title}`;
    case "payment":
      return `payment:${route.result}:${route.publicId}`;
    case "checkout":
      return `checkout:${route.publicId}:${route.totalPrice}`;
    case "sbpBanks":
      return `sbpBanks:${route.publicId}:${route.totalPrice}`;
    default:
      return route.name;
  }
}

export function deserializeRoute(key: string): AppRoute | null {
  if (key === "main") return { name: "main" };
  if (key === "cart") return { name: "cart" };
  if (key === "orders") return { name: "orders" };
  if (key === "profile") return { name: "profile" };
  if (key.startsWith("category:")) {
    const categoryId = Number(key.slice("category:".length));
    return Number.isFinite(categoryId) ? { name: "category", categoryId } : null;
  }
  if (key.startsWith("product:")) {
    const itemId = Number(key.slice("product:".length));
    return Number.isFinite(itemId) ? { name: "product", itemId } : null;
  }
  if (key.startsWith("missing:")) {
    return { name: "missing", title: key.slice("missing:".length) };
  }
  if (key.startsWith("checkout:")) {
    const rest = key.slice("checkout:".length);
    const separator = rest.lastIndexOf(":");
    if (separator === -1) return null;
    const publicId = rest.slice(0, separator);
    const totalPrice = Number(rest.slice(separator + 1));
    if (publicId && Number.isFinite(totalPrice)) {
      return { name: "checkout", publicId, totalPrice };
    }
  }
  if (key.startsWith("sbpBanks:")) {
    const rest = key.slice("sbpBanks:".length);
    const separator = rest.lastIndexOf(":");
    if (separator === -1) return null;
    const publicId = rest.slice(0, separator);
    const totalPrice = Number(rest.slice(separator + 1));
    if (publicId && Number.isFinite(totalPrice)) {
      return { name: "sbpBanks", publicId, totalPrice };
    }
  }
  if (key.startsWith("payment:")) {
    const rest = key.slice("payment:".length);
    const separator = rest.indexOf(":");
    if (separator === -1) return null;
    const result = rest.slice(0, separator);
    const publicId = rest.slice(separator + 1);
    if ((result === "success" || result === "fail") && publicId) {
      return { name: "payment", result, publicId };
    }
  }
  return null;
}

export function routeToHash(route: AppRoute): string {
  switch (route.name) {
    case "main":
      return MAIN_HASH;
    case "cart":
      return "#/cart";
    case "orders":
      return "#/orders";
    case "profile":
      return "#/profile";
    case "category":
      return `#/category/${route.categoryId}`;
    case "product":
      return `#/product/${route.itemId}`;
    case "missing":
      return `#/missing/${encodeURIComponent(route.title)}`;
    case "payment":
      return `#/payment/${route.result}/${encodeURIComponent(route.publicId)}`;
    case "checkout":
      return `#/checkout/${encodeURIComponent(route.publicId)}/${route.totalPrice}`;
    case "sbpBanks":
      return `#/sbp/${encodeURIComponent(route.publicId)}/${route.totalPrice}`;
    default:
      return MAIN_HASH;
  }
}

export function hashToRoute(hash: string): AppRoute | null {
  const normalized = hash.replace(/^#/, "").replace(/^\//, "");
  if (normalized === "" || normalized === "main") return { name: "main" };
  if (normalized === "cart") return { name: "cart" };
  if (normalized === "orders") return { name: "orders" };
  if (normalized === "profile") return { name: "profile" };

  const segments = normalized.split("/").filter(Boolean);
  const [head, second] = segments;
  if (head === "category") {
    const categoryId = Number(second);
    return Number.isFinite(categoryId) ? { name: "category", categoryId } : null;
  }
  if (head === "product") {
    const itemId = Number(second);
    return Number.isFinite(itemId) ? { name: "product", itemId } : null;
  }
  if (head === "missing" && second) {
    return { name: "missing", title: decodeURIComponent(second) };
  }
  if (head === "payment" && (second === "success" || second === "fail")) {
    const publicId = decodeURIComponent(segments.slice(2).join("/"));
    if (publicId) return { name: "payment", result: second, publicId };
  }
  if (head === "checkout" && second) {
    const publicId = decodeURIComponent(second);
    const totalPrice = Number(segments[2]);
    if (publicId && Number.isFinite(totalPrice)) {
      return { name: "checkout", publicId, totalPrice };
    }
  }
  if (head === "sbp" && second) {
    const publicId = decodeURIComponent(second);
    const totalPrice = Number(segments[2]);
    if (publicId && Number.isFinite(totalPrice)) {
      return { name: "sbpBanks", publicId, totalPrice };
    }
  }
  return null;
}

function historyUrl(hash: string): string {
  return `${window.location.pathname}${hash}`;
}

function writeHistory(route: AppRoute, mode: "push" | "replace"): void {
  const hash = routeToHash(route);
  const state = { appRoute: serializeRoute(route) } satisfies HistoryState;
  const url = historyUrl(hash);
  if (mode === "replace") {
    window.history.replaceState(state, "", url);
    return;
  }
  window.history.pushState(state, "", url);
}

export function seedHistoryStack(route: AppRoute): void {
  writeHistory({ name: "main" }, "replace");
  if (route.name === "main") {
    writeHistory({ name: "main" }, "push");
    return;
  }
  writeHistory(route, "push");
}

export function pushHistoryRoute(route: AppRoute): void {
  writeHistory(route, "push");
}

export function resetHistoryToMain(): void {
  writeHistory({ name: "main" }, "replace");
  writeHistory({ name: "main" }, "push");
}

export function readRouteFromLocation(state: unknown = window.history.state): AppRoute {
  const fromHash = hashToRoute(window.location.hash);
  if (fromHash) return fromHash;

  const key = (state as HistoryState | null)?.appRoute;
  if (typeof key === "string") {
    const parsed = deserializeRoute(key);
    if (parsed) return parsed;
  }
  return { name: "main" };
}

export function isHistoryManagedRoute(route: AppRoute): boolean {
  return route.name !== "startup" && route.name !== "blocked";
}

export function seedInitialBrowserHistory(): void {
  if (new URLSearchParams(window.location.search).has("payment")) return;
  if ((window.history.state as HistoryState | null)?.appRoute) return;

  const hash = window.location.hash;
  if (!hash || hash === "#") {
    window.history.replaceState({ appRoute: MAIN_KEY } satisfies HistoryState, "", historyUrl(MAIN_HASH));
    window.history.pushState({ appRoute: MAIN_KEY } satisfies HistoryState, "", historyUrl(MAIN_HASH));
    return;
  }

  const parsed = hashToRoute(hash);
  if (parsed && isHistoryManagedRoute(parsed)) {
    window.history.replaceState(
      { appRoute: serializeRoute(parsed) } satisfies HistoryState,
      "",
      historyUrl(routeToHash(parsed)),
    );
  }
}
