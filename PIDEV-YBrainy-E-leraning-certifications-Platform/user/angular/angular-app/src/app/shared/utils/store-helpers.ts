export function uuid(): string {
  // Good-enough stable id for UI-only CRUD
  return Math.random().toString(16).slice(2) + Date.now().toString(16);
}

export function nowIso(): string {
  return new Date().toISOString();
}

export function readJsonFromLocalStorage<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

export function writeJsonToLocalStorage<T>(key: string, value: T): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* ignore */
  }
}


