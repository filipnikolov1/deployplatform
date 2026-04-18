"use client";

const BACKEND_UPDATE_PENDING_KEY = "launchpad:backend-update-pending";
const BACKEND_UPDATE_EVENT = "launchpad:backend-update-state";
const BACKEND_UPDATE_TTL_MS = 20 * 60 * 1000;

function emitBackendUpdateEvent() {
  window.dispatchEvent(new Event(BACKEND_UPDATE_EVENT));
}

export function markBackendUpdatePending() {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(BACKEND_UPDATE_PENDING_KEY, String(Date.now()));
  emitBackendUpdateEvent();
}

export function clearBackendUpdatePending() {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.removeItem(BACKEND_UPDATE_PENDING_KEY);
  emitBackendUpdateEvent();
}

export function hasPendingBackendUpdate(): boolean {
  if (typeof window === "undefined") {
    return false;
  }

  const raw = window.localStorage.getItem(BACKEND_UPDATE_PENDING_KEY);
  if (!raw) {
    return false;
  }

  const startedAt = Number(raw);
  if (!Number.isFinite(startedAt) || Date.now() - startedAt > BACKEND_UPDATE_TTL_MS) {
    window.localStorage.removeItem(BACKEND_UPDATE_PENDING_KEY);
    emitBackendUpdateEvent();
    return false;
  }

  return true;
}

export function subscribeToBackendUpdateState(onChange: () => void) {
  if (typeof window === "undefined") {
    return () => {};
  }

  const handleStorage = (event: StorageEvent) => {
    if (event.key === BACKEND_UPDATE_PENDING_KEY) {
      onChange();
    }
  };

  window.addEventListener("storage", handleStorage);
  window.addEventListener(BACKEND_UPDATE_EVENT, onChange);

  return () => {
    window.removeEventListener("storage", handleStorage);
    window.removeEventListener(BACKEND_UPDATE_EVENT, onChange);
  };
}
