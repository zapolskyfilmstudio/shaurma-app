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
  | { name: "payment"; result: "success" | "fail"; publicId: string };

type HistoryState = {
  appRoute?: string;
};

function historyPathname(): string {
  return window.location.pathname;
}

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

export function seedHistoryStack(route: AppRoute): void {
  const pathname = historyPathname();
  const mainKey = serializeRoute({ name: "main" });
  window.history.replaceState({ appRoute: mainKey } satisfies HistoryState, "", pathname);
  if (route.name === "main") {
    window.history.pushState({ appRoute: mainKey } satisfies HistoryState, "", pathname);
    return;
  }
  window.history.pushState({ appRoute: serializeRoute(route) } satisfies HistoryState, "", pathname);
}

export function pushHistoryRoute(route: AppRoute): void {
  window.history.pushState(
    { appRoute: serializeRoute(route) } satisfies HistoryState,
    "",
    historyPathname(),
  );
}

export function resetHistoryToMain(): void {
  const pathname = historyPathname();
  const mainKey = serializeRoute({ name: "main" });
  window.history.replaceState({ appRoute: mainKey } satisfies HistoryState, "", pathname);
  window.history.pushState({ appRoute: mainKey } satisfies HistoryState, "", pathname);
}

export function routeFromPopState(state: unknown): AppRoute {
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
