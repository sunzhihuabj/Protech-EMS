import { useEffect } from 'react';
import { ColorPicker, Space, Switch, Tooltip } from 'antd';
import { THEMES, currentTheme, useTheme, applyThemeCss } from '../theme/theme';

/** FR-M00-014：12 主题 × 明暗切换（tokens.json 单一事实源） */
export function ThemeSwitcher() {
  const { themeKey, mode, setTheme, setMode } = useTheme();

  useEffect(() => {
    applyThemeCss();
  }, [themeKey, mode]);

  return (
    <Space size={8}>
      <Tooltip title="12 套主题（FR-M00-014，visual/ 资产令牌）">
        <ColorPicker
          size="small"
          value={currentTheme().pri['500']}
          presets={[
            {
              label: 'Protech 12 主题',
              colors: THEMES.map((t) => t.pri['500']),
            },
          ]}
          onChange={(color) => {
            const hex = color.toHexString().toUpperCase();
            const hit = THEMES.find((t) => t.pri['500'].toUpperCase() === hex);
            if (hit) setTheme(hit.key);
          }}
          showText={false}
        />
      </Tooltip>
      <Switch
        size="small"
        checkedChildren="暗"
        unCheckedChildren="亮"
        checked={mode === 'dark'}
        onChange={(v) => setMode(v ? 'dark' : 'light')}
      />
    </Space>
  );
}
