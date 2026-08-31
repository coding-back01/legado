import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const SCRIPT_PATH = fileURLToPath(import.meta.url)
const WEB_ROOT = path.resolve(path.dirname(SCRIPT_PATH), '..')
const SOURCE_ROOT = path.join(WEB_ROOT, 'src')

export const EXPECTED_LINKS = [
  {
    file: 'src/components/SourceHelp.vue',
    text: 'APP帮助文档',
    href: '/help/#appHelp',
  },
  {
    file: 'src/components/SourceHelp.vue',
    text: '书源制作教程',
    href: '/help/#ruleHelp',
  },
  {
    file: 'src/components/SourceHelp.vue',
    text: 'js变量和函数',
    href: '/help/#jsHelp',
  },
  {
    file: 'src/components/SourceHelp.vue',
    text: 'xpath语法教程',
    href: '/help/#xpathHelp',
  },
  {
    file: 'src/components/SourceHelp.vue',
    text: '正则表达式教程',
    href: '/help/#regexHelp',
  },
  {
    file: 'src/components/SourceHelp.vue',
    text: 'txt目录正则说明',
    href: '/help/#txtTocRuleHelp',
  },
  {
    file: 'src/components/SourceHelp.vue',
    text: '书源调试说明',
    href: '/help/#debugHelp',
  },
  {
    file: 'src/components/SourceHelp.vue',
    text: '在线朗读规则',
    href: '/help/#httpTTSHelp',
  },
  {
    file: 'src/components/SourceHelp.vue',
    text: 'WebDav书籍简明使用教程',
    href: '/help/#webDavBookHelp',
  },
  {
    file: 'src/components/SourceHelp.vue',
    text: 'WebDav备份教程',
    href: '/help/#webDavHelp',
  },
  {
    file: 'src/components/SourceHelp.vue',
    text: '正则表达式在线验证工具',
    href: 'https://regexr-cn.com/',
  },
]

const ALLOWED_ATTRIBUTES = new Set([':icon', 'href', 'rel', 'target'])
const REQUIRED_REL_TOKENS = new Set(['noopener', 'noreferrer'])
const OPEN_TAG_PATTERN = /<(el-link|ElLink)(?=[\s/>])/g

function fail(file, message) {
  throw new Error(`${file}: ${message}`)
}

function findStartTagEnd(source, start, file) {
  let quote = null

  for (let index = start; index < source.length; index += 1) {
    const character = source[index]
    if (quote !== null) {
      if (character === quote) quote = null
      continue
    }
    if (character === '"' || character === "'") {
      quote = character
    } else if (character === '>') {
      return index
    } else if (character === '<') {
      fail(file, 'el-link 开始标签未正确闭合')
    }
  }

  fail(file, 'el-link 开始标签缺少闭合符号')
}

function parseAttributes(rawAttributes, file) {
  const attributes = new Map()
  let cursor = 0

  while (cursor < rawAttributes.length) {
    while (/\s/.test(rawAttributes[cursor] ?? '')) cursor += 1
    if (cursor >= rawAttributes.length) break
    if (rawAttributes[cursor] === '/') {
      fail(file, '受管 el-link 不允许自闭合')
    }

    const nameStart = cursor
    while (
      cursor < rawAttributes.length &&
      !/[\s=]/.test(rawAttributes[cursor])
    ) {
      cursor += 1
    }
    const name = rawAttributes.slice(nameStart, cursor).toLowerCase()
    if (name.length === 0) fail(file, 'el-link 包含无法解析的属性名')

    while (/\s/.test(rawAttributes[cursor] ?? '')) cursor += 1
    if (rawAttributes[cursor] !== '=') {
      fail(file, `属性 ${name} 必须使用显式字面量值`)
    }
    cursor += 1
    while (/\s/.test(rawAttributes[cursor] ?? '')) cursor += 1

    const quote = rawAttributes[cursor]
    if (quote !== '"' && quote !== "'") {
      fail(file, `属性 ${name} 必须使用引号包裹的字面量值`)
    }
    cursor += 1
    const valueStart = cursor
    while (cursor < rawAttributes.length && rawAttributes[cursor] !== quote) {
      cursor += 1
    }
    if (cursor >= rawAttributes.length) {
      fail(file, `属性 ${name} 的引号未闭合`)
    }
    const value = rawAttributes.slice(valueStart, cursor)
    cursor += 1

    if (attributes.has(name)) fail(file, `属性 ${name} 重复出现`)
    attributes.set(name, value)
  }

  for (const name of attributes.keys()) {
    if (!ALLOWED_ATTRIBUTES.has(name)) {
      fail(file, `受管 el-link 包含未批准属性 ${name}`)
    }
  }

  if (attributes.get(':icon') !== 'Link') {
    fail(file, '受管 el-link 必须保留 :icon="Link"')
  }
  if (!attributes.has('href')) {
    fail(file, '受管 el-link 必须且只能使用普通字面量 href')
  }
  if (attributes.get('target') !== '_blank') {
    fail(file, '受管 el-link 必须保留 target="_blank"')
  }

  const relTokens = (attributes.get('rel') ?? '').split(/\s+/).filter(Boolean)
  const actualRelTokens = new Set(relTokens)
  if (
    relTokens.length !== REQUIRED_REL_TOKENS.size ||
    actualRelTokens.size !== REQUIRED_REL_TOKENS.size ||
    [...REQUIRED_REL_TOKENS].some(token => !actualRelTokens.has(token))
  ) {
    fail(file, '受管 el-link 必须显式使用 rel="noopener noreferrer"')
  }

  return attributes
}

export function scanVueSource(source, file) {
  const links = []
  const openingPattern = new RegExp(
    OPEN_TAG_PATTERN.source,
    OPEN_TAG_PATTERN.flags,
  )
  let openingMatch

  while ((openingMatch = openingPattern.exec(source)) !== null) {
    const tagName = openingMatch[1]
    const startTagEnd = findStartTagEnd(source, openingPattern.lastIndex, file)
    const attributes = parseAttributes(
      source.slice(openingPattern.lastIndex, startTagEnd),
      file,
    )
    const contentStart = startTagEnd + 1
    const closingPattern = new RegExp(`</${tagName}\\s*>`, 'g')
    closingPattern.lastIndex = contentStart
    const closingMatch = closingPattern.exec(source)
    if (closingMatch === null) fail(file, `缺少 </${tagName}>`)

    openingPattern.lastIndex = contentStart
    const nestedMatch = openingPattern.exec(source)
    if (nestedMatch !== null && nestedMatch.index < closingMatch.index) {
      fail(file, '受管 el-link 不允许嵌套')
    }

    const rawText = source.slice(contentStart, closingMatch.index)
    if (rawText.includes('<')) {
      fail(file, '受管 el-link 的可见文字必须是纯文本')
    }
    const text = rawText.replace(/\s+/g, ' ').trim()
    if (text.length === 0) fail(file, '受管 el-link 的可见文字不能为空')

    links.push({
      file,
      text,
      href: attributes.get('href'),
    })
    openingPattern.lastIndex = closingPattern.lastIndex
  }

  const closingTagCount = source.match(/<\/(?:el-link|ElLink)\s*>/g)?.length ?? 0
  if (closingTagCount !== links.length) {
    fail(file, 'el-link 开始标签与结束标签数量不一致')
  }

  return links
}

function linkKey(link) {
  return JSON.stringify([link.file, link.text, link.href])
}

export function verifyLinkInventory(actualLinks, expectedLinks = EXPECTED_LINKS) {
  const actualCounts = new Map()
  for (const link of actualLinks) {
    const key = linkKey(link)
    actualCounts.set(key, (actualCounts.get(key) ?? 0) + 1)
  }

  const expectedCounts = new Map()
  for (const link of expectedLinks) {
    const key = linkKey(link)
    expectedCounts.set(key, (expectedCounts.get(key) ?? 0) + 1)
  }

  const unexpected = [...actualCounts].filter(
    ([key, count]) => expectedCounts.get(key) !== count,
  )
  const missing = [...expectedCounts].filter(
    ([key, count]) => actualCounts.get(key) !== count,
  )
  if (unexpected.length > 0 || missing.length > 0) {
    throw new Error(
      `静态链接清单不一致：预期 ${expectedLinks.length} 项，实际 ${actualLinks.length} 项；` +
        `未批准或重复 ${unexpected.length} 项，缺失 ${missing.length} 项`,
    )
  }
}

async function collectVueFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const files = []

  for (const entry of entries.sort((left, right) => left.name.localeCompare(right.name))) {
    const entryPath = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      files.push(...(await collectVueFiles(entryPath)))
    } else if (entry.isFile() && entry.name.endsWith('.vue')) {
      files.push(entryPath)
    }
  }
  return files
}

export async function verifyRepositoryLinks(
  sourceRoot = SOURCE_ROOT,
  webRoot = WEB_ROOT,
) {
  const links = []
  for (const filePath of await collectVueFiles(sourceRoot)) {
    const file = path.relative(webRoot, filePath).split(path.sep).join('/')
    links.push(...scanVueSource(await readFile(filePath, 'utf8'), file))
  }
  verifyLinkInventory(links)
  return links
}

if (process.argv[1] && path.resolve(process.argv[1]) === SCRIPT_PATH) {
  try {
    const links = await verifyRepositoryLinks()
    console.log(`静态链接安全合同通过：${links.length} 个受管链接`)
  } catch (error) {
    console.error(error instanceof Error ? error.message : error)
    process.exitCode = 1
  }
}
