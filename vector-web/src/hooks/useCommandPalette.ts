"use client";

/**
 * useCommandPalette — lightweight module-level store (no zustand).
 * Uses a Set of listeners so any component can subscribe without context.
 *
 * Global Cmd+K / Ctrl+K binding is wired inside AppShell via useEffect.
 */

type Listener = (open: boolean) => void;

let _open = false;
const _listeners = new Set<Listener>();

function notify() {
  _listeners.forEach((l) => l(_open));
}

export const commandPaletteStore = {
  open(): void {
    _open = true;
    notify();
  },
  close(): void {
    _open = false;
    notify();
  },
  toggle(): void {
    _open = !_open;
    notify();
  },
  getSnapshot(): boolean {
    return _open;
  },
  subscribe(listener: Listener): () => void {
    _listeners.add(listener);
    return () => _listeners.delete(listener);
  },
};

import { useEffect, useState } from "react";

export function useCommandPalette(): {
  isOpen: boolean;
  open: () => void;
  close: () => void;
  toggle: () => void;
} {
  const [isOpen, setIsOpen] = useState<boolean>(_open);

  useEffect(() => {
    const unsub = commandPaletteStore.subscribe(setIsOpen);
    // Sync in case state changed before mount
    setIsOpen(commandPaletteStore.getSnapshot());
    return unsub;
  }, []);

  return {
    isOpen,
    open: commandPaletteStore.open,
    close: commandPaletteStore.close,
    toggle: commandPaletteStore.toggle,
  };
}
