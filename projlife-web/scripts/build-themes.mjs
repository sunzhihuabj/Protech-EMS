/**
 * 12 套预编译主题构建（03 册 §6A.3：Element UI 2.15 必须走构建期预编译主题包，
 * 禁止运行时 CSS 变量全量替换 hack；色值唯一来源 docs/tokens.reference.json，ADR-004）。
 *
 * 产物：
 *   public/theme/t{1..12}.css   完整 Element 样式（主色已替换）
 *   public/theme/fonts/         图标字体
 *   src/theme/themes.json       主题清单（名称/档位/主色，供外观面板渲染）
 */
import sass from 'sass'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const webRoot = path.resolve(__dirname, '..')
const repoRoot = path.resolve(webRoot, '..')
const tokensPath = path.resolve(repoRoot, 'docs', 'tokens.reference.json')
const elementSrc = path.resolve(webRoot, 'node_modules', 'element-ui', 'packages', 'theme-chalk', 'src')

const tokens = JSON.parse(fs.readFileSync(tokensPath, 'utf-8'))
const themes = tokens.themes
if (!Array.isArray(themes) || themes.length !== 12) {
  throw new Error(`主题清单应为 12 套，实际 ${themes && themes.length}`)
}

const outDir = path.resolve(webRoot, 'public', 'theme')
fs.mkdirSync(outDir, { recursive: true })

// 字体一次性拷贝（图标字体 el-icon）
const fontsSrc = path.join(elementSrc, 'fonts')
const fontsDst = path.join(outDir, 'fonts')
if (fs.existsSync(fontsSrc) && !fs.existsSync(fontsDst)) {
  fs.cpSync(fontsSrc, fontsDst, { recursive: true })
}

const manifest = []
for (const theme of themes) {
  const primary = theme.pri && theme.pri['500']
  if (!primary) {
    throw new Error(`主题 ${theme.key} 缺少 pri.500 主色`)
  }
  const scss = [
    `// 生成于构建期，禁止手改（色源：docs/tokens.reference.json ${theme.key}）`,
    `$--color-primary: ${primary};`,
    `$--font-path: './fonts';`,
    `@import "index";`
  ].join('\n')
  const result = sass.renderSync({
    data: scss,
    includePaths: [elementSrc],
    outputStyle: 'compressed'
  })
  const outFile = path.join(outDir, `${theme.key}.css`)
  fs.writeFileSync(outFile, result.css)
  manifest.push({
    key: theme.key,
    name: theme.name,
    grade: theme.grade,
    gradeKey: theme.gradeKey,
    p500: primary,
    p600: theme.pri['600'],
    p800: theme.pri['800']
  })
  console.log(`theme ${theme.key} (${theme.name}) -> ${path.relative(webRoot, outFile)} ${primary}`)
}

fs.writeFileSync(
  path.resolve(webRoot, 'src', 'theme', 'themes.json'),
  JSON.stringify({ storageKey: tokens.meta.storageKey, defaultTheme: tokens.meta.defaultTheme, themes: manifest }, null, 2)
)
console.log(`done: ${manifest.length} themes compiled`)
