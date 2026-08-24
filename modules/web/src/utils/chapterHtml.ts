type ImageSourceResolver = (source: string) => string

type ChapterContentToken =
  { type: 'text'; value: string } | { type: 'image'; source: string }

type ParsedImageTag = {
  end: number
  source: string
}

type ParsedAttributeValue = {
  next: number
  value: string
}

const htmlEntities: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
}

const htmlWhitespace = new Set([' ', '\t', '\n', '\f', '\r'])
const rawTextTags = new Set([
  'iframe',
  'noembed',
  'noframes',
  'noscript',
  'plaintext',
  'script',
  'style',
  'template',
  'textarea',
  'title',
  'xmp',
])

const isHtmlWhitespace = (character: string): boolean => {
  return htmlWhitespace.has(character)
}

const isAttributeNameCharacter = (character: string): boolean => {
  return (
    character.length > 0 &&
    !isHtmlWhitespace(character) &&
    !['"', "'", '=', '<', '>', '/', '`'].includes(character)
  )
}

const escapeHtml = (value: string): string => {
  return value.replace(/[&<>"']/g, character => htmlEntities[character])
}

const findMarkupEnd = (content: string, start: number): number | undefined => {
  let quote: '"' | "'" | undefined

  for (let position = start + 1; position < content.length; position += 1) {
    const character = content[position]
    if (quote !== undefined) {
      if (character === quote) quote = undefined
    } else if (character === '"' || character === "'") {
      quote = character
    } else if (character === '>') {
      return position
    }
  }

  return undefined
}

const getMarkupName = (
  content: string,
  start: number,
): { closing: boolean; name: string } | undefined => {
  let position = start + 1
  const closing = content[position] === '/'
  if (closing) position += 1

  const nameStart = position
  while (/[A-Za-z0-9:-]/.test(content[position] ?? '')) position += 1
  if (position === nameStart) return undefined

  return {
    closing,
    name: content.slice(nameStart, position).toLowerCase(),
  }
}

const findRawTextEnd = (
  content: string,
  lowercaseContent: string,
  searchStart: number,
  tagName: string,
): number => {
  if (tagName === 'plaintext') return content.length

  const closingPrefix = `</${tagName}`
  let closingStart = lowercaseContent.indexOf(closingPrefix, searchStart)

  while (closingStart !== -1) {
    const boundary = lowercaseContent[closingStart + closingPrefix.length] ?? ''
    if (boundary === '>' || boundary === '/' || isHtmlWhitespace(boundary)) {
      const closingEnd = findMarkupEnd(content, closingStart)
      if (closingEnd !== undefined) return closingEnd + 1
      return content.length
    }
    closingStart = lowercaseContent.indexOf(closingPrefix, closingStart + 1)
  }

  return content.length
}

const findJsonObjectEnd = (
  content: string,
  objectStart: number,
  cachedEnds: Map<number, number | undefined>,
): number | undefined => {
  if (cachedEnds.has(objectStart)) return cachedEnds.get(objectStart)

  const objectStarts: number[] = []
  let escaped = false
  let inString = false

  for (let position = objectStart; position < content.length; position += 1) {
    const character = content[position]
    if (inString) {
      if (escaped) {
        escaped = false
      } else if (character === '\\') {
        escaped = true
      } else if (character === '"') {
        inString = false
      }
      continue
    }

    if (character === '"') {
      inString = true
    } else if (character === '{') {
      objectStarts.push(position)
    } else if (character === '}') {
      const matchingStart = objectStarts.pop()
      if (matchingStart === undefined) break
      cachedEnds.set(matchingStart, position)
      if (matchingStart === objectStart) return position
    }
  }

  for (const unmatchedStart of objectStarts) {
    cachedEnds.set(unmatchedStart, undefined)
  }
  cachedEnds.set(objectStart, undefined)
  return undefined
}

const readQuotedAttributeValue = (
  content: string,
  valueStart: number,
  quote: '"' | "'",
): ParsedAttributeValue | undefined => {
  const valueEnd = content.indexOf(quote, valueStart)
  if (valueEnd === -1) return undefined
  return {
    next: valueEnd + 1,
    value: content.slice(valueStart, valueEnd),
  }
}

/**
 * Legado 允许图片地址追加 `,{...}` 请求参数。参数来自 JSON.stringify，内部引号与
 * 外层 HTML 引号可能相同，因此先识别该受支持后缀，再寻找真正的属性结束引号。
 */
const readQuotedImageSource = (
  content: string,
  valueStart: number,
  quote: '"' | "'",
  cachedJsonEnds: Map<number, number | undefined>,
): ParsedAttributeValue | undefined => {
  for (let position = valueStart; position < content.length; position += 1) {
    const character = content[position]
    if (character === quote) {
      return {
        next: position + 1,
        value: content.slice(valueStart, position),
      }
    }
    if (character !== ',') continue

    let objectStart = position + 1
    while (isHtmlWhitespace(content[objectStart] ?? '')) objectStart += 1
    if (content[objectStart] !== '{') continue

    const objectEnd = findJsonObjectEnd(content, objectStart, cachedJsonEnds)
    if (objectEnd === undefined) continue
    if (content[objectEnd + 1] !== quote) {
      position = objectEnd
      continue
    }
    return {
      next: objectEnd + 2,
      value: content.slice(valueStart, objectEnd + 1),
    }
  }

  return undefined
}

const parseImageTag = (
  content: string,
  start: number,
  cachedJsonEnds: Map<number, number | undefined>,
): ParsedImageTag | undefined => {
  if (
    content.slice(start, start + 4) !== '<img' ||
    !isHtmlWhitespace(content[start + 4] ?? '')
  ) {
    return undefined
  }

  let position = start + 4
  let source: string | undefined
  let sourceCount = 0

  while (position < content.length) {
    const whitespaceStart = position
    while (isHtmlWhitespace(content[position] ?? '')) position += 1

    if (content[position] === '>') {
      return source === undefined ? undefined : { end: position, source }
    }
    if (content[position] === '/') {
      position += 1
      while (isHtmlWhitespace(content[position] ?? '')) position += 1
      if (content[position] === '>') {
        return source === undefined ? undefined : { end: position, source }
      }
      return undefined
    }
    if (position === whitespaceStart || position >= content.length) {
      return undefined
    }

    const nameStart = position
    while (isAttributeNameCharacter(content[position] ?? '')) position += 1
    if (position === nameStart) return undefined

    const name = content.slice(nameStart, position)
    while (isHtmlWhitespace(content[position] ?? '')) position += 1

    let hasValue = false
    let quotedValue = false
    let value = ''
    if (content[position] === '=') {
      hasValue = true
      position += 1
      while (isHtmlWhitespace(content[position] ?? '')) position += 1

      const quote = content[position]
      if (quote === '"' || quote === "'") {
        quotedValue = true
        const parsedValue =
          name === 'src'
            ? readQuotedImageSource(
                content,
                position + 1,
                quote,
                cachedJsonEnds,
              )
            : readQuotedAttributeValue(content, position + 1, quote)
        if (parsedValue === undefined) return undefined
        value = parsedValue.value
        position = parsedValue.next
      } else {
        const valueStart = position
        while (
          position < content.length &&
          !isHtmlWhitespace(content[position]) &&
          content[position] !== '>'
        ) {
          if (['"', "'", '=', '<', '`'].includes(content[position])) {
            return undefined
          }
          position += 1
        }
        if (position === valueStart) return undefined
        value = content.slice(valueStart, position)
      }
    }

    if (name === 'src') {
      sourceCount += 1
      if (
        sourceCount !== 1 ||
        !hasValue ||
        !quotedValue ||
        value.trim().length === 0
      ) {
        return undefined
      }
      source = value
    }
  }

  return undefined
}

const tokenizeChapterContent = (content: string): ChapterContentToken[] => {
  const tokens: ChapterContentToken[] = []
  const cachedJsonEnds = new Map<number, number | undefined>()
  const lowercaseContent = content.toLowerCase()
  const lastTagEnd = content.lastIndexOf('>')
  let textStart = 0
  let searchPosition = 0

  while (searchPosition < content.length) {
    const candidateStart = content.indexOf('<', searchPosition)
    if (candidateStart === -1) break
    if (candidateStart > lastTagEnd) break

    if (content.startsWith('<!--', candidateStart)) {
      const commentEnd = content.indexOf('-->', candidateStart + 4)
      searchPosition = commentEnd === -1 ? content.length : commentEnd + 3
      continue
    }

    const image = parseImageTag(content, candidateStart, cachedJsonEnds)
    if (image !== undefined) {
      if (candidateStart > textStart) {
        tokens.push({
          type: 'text',
          value: content.slice(textStart, candidateStart),
        })
      }
      tokens.push({ type: 'image', source: image.source })
      textStart = image.end + 1
      searchPosition = textStart
      continue
    }

    const markupEnd = findMarkupEnd(content, candidateStart)
    if (markupEnd === undefined) {
      break
    }

    const markup = getMarkupName(content, candidateStart)
    if (
      markup !== undefined &&
      !markup.closing &&
      rawTextTags.has(markup.name)
    ) {
      searchPosition = findRawTextEnd(
        content,
        lowercaseContent,
        markupEnd + 1,
        markup.name,
      )
    } else {
      searchPosition = markupEnd + 1
    }
  }

  if (textStart < content.length) {
    tokens.push({ type: 'text', value: content.slice(textStart) })
  }

  return tokens
}

/**
 * 章节正文在服务端已经被归一为普通文本和 canonical 小写 img 标签。这里再次建立浏览器
 * 边界：普通内容全部按文本转义，只重建唯一且非空的 src，避免书源内容形成其他标签或
 * 事件属性。非 canonical 标签按文本处理，防止 entity 解码重新引入新的浏览器语义。
 */
export const renderSafeChapterHtml = (
  content: string,
  resolveImageSource: ImageSourceResolver = source => source,
): string => {
  return tokenizeChapterContent(content)
    .map(token => {
      if (token.type === 'text') return escapeHtml(token.value)
      return `<img src="${escapeHtml(resolveImageSource(token.source))}">`
    })
    .join('')
}

export const getStandaloneImageSource = (
  content: string,
): string | undefined => {
  let source: string | undefined

  for (const token of tokenizeChapterContent(content)) {
    if (token.type === 'text') {
      if (token.value.trim().length > 0) return undefined
    } else if (source === undefined) {
      source = token.source
    } else {
      return undefined
    }
  }

  return source
}

export const calculateChapterWordCount = (content: string): number => {
  return tokenizeChapterContent(content).reduce((length, token) => {
    return length + (token.type === 'text' ? token.value.length : 1)
  }, 0)
}

export const createImageFallbackResolver = (
  resolveFallback: ImageSourceResolver,
): ((target: object, source: string) => string | undefined) => {
  const attemptedSources = new WeakMap<object, Set<string>>()

  return (target, source) => {
    let sources = attemptedSources.get(target)
    if (sources === undefined) {
      sources = new Set<string>()
      attemptedSources.set(target, sources)
    }
    if (sources.has(source)) return undefined

    sources.add(source)
    const fallback = resolveFallback(source)
    if (fallback === source) return undefined

    sources.add(fallback)
    return fallback
  }
}
