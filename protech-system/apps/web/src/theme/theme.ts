import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import tokensJson from './tokens.json';

/**
 * FR-M00-014 主题系统：12 主题（tokens.json 单一事实源，来自 visual/ 资产）× 明/暗双模式。
 * CSS 变量注入 --pt-pri-*；antd colorPrimary 同步 pri.500；默认深色导航+浅色内容（07 册 §五）。
 */
export interface ThemeDef {
  key: string;
  name: string;
  grade: string;
  pri: Record<string, string>;
  neu: Record<string, string>;
}

export const THEMES = tokensJson.themes as unknown as ThemeDef[];

interface ThemeState {
  themeKey: string;
  mode: 'light' | 'dark';
  setTheme: (key: string) => void;
  setMode: (mode: 'light' | 'dark') => void;
}

export const useTheme = create<ThemeState>()(
  persist(
    (set) => ({
      themeKey: tokensJson.meta?.defaultTheme ?? 't1',
      mode: 'light',
      setTheme: (themeKey) => set({ themeKey }),
      setMode: (mode) => set({ mode }),
    }),
    { name: 'protech-theme' },
  ),
);

export function currentTheme(): ThemeDef {
  return THEMES.find((t) => t.key === useTheme.getState().themeKey) ?? THEMES[0];
}

/** 把主题色阶注入 :root CSS 变量（projlife-theme.css 消费） */
export function applyThemeCss() {
  const { themeKey, mode } = useTheme.getState();
  const theme = THEMES.find((t) => t.key === themeKey) ?? THEMES[0];
  const root = document.documentElement;
  for (const [step, color] of Object.entries(theme.pri)) {
    root.style.setProperty(`--pt-pri-${step}`, color);
  }
  for (const [step, color] of Object.entries(theme.neu)) {
    root.style.setProperty(`--pt-neu-${step}`, color);
  }
  root.style.setProperty('--pt-mode', mode);
  root.dataset.ptMode = mode;
}
