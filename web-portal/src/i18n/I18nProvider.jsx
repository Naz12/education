import React, { createContext, useContext, useEffect, useMemo, useState } from "react";
import { STRINGS } from "./strings.js";

const STORAGE_KEY = "portal_locale";

const I18nContext = createContext(null);

export function I18nProvider({ children }) {
  const [locale, setLocaleState] = useState(() => {
    try {
      const s = localStorage.getItem(STORAGE_KEY);
      return s === "am" || s === "en" ? s : "en";
    } catch {
      return "en";
    }
  });

  const setLocale = (next) => {
    const v = next === "am" ? "am" : "en";
    setLocaleState(v);
    try {
      localStorage.setItem(STORAGE_KEY, v);
    } catch {
      /* ignore */
    }
  };

  useEffect(() => {
    document.documentElement.lang = locale === "am" ? "am" : "en";
  }, [locale]);

  const value = useMemo(
    () => ({
      locale,
      setLocale,
      str: STRINGS[locale] || STRINGS.en
    }),
    [locale]
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  const ctx = useContext(I18nContext);
  if (!ctx) {
    throw new Error("useI18n must be used within I18nProvider");
  }
  return ctx;
}
