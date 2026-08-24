import { strictEqual, doesNotMatch, match, ok } from 'node:assert'
import { performance } from 'node:perf_hooks'
import { test } from 'node:test'

import {
  calculateChapterWordCount,
  createImageFallbackResolver,
  getStandaloneImageSource,
  renderSafeChapterHtml,
} from '../src/utils/chapterHtml.js'

test('普通章节文本按文本语义转义', () => {
  strictEqual(
    renderSafeChapterHtml(`A & B < C > D "双" '单'`),
    'A &amp; B &lt; C &gt; D &quot;双&quot; &#39;单&#39;',
  )
})

test('只保留图片地址并移除图片上的其他属性', () => {
  const html = renderSafeChapterHtml(
    '前<img src="/cover.png" onerror="unexpected()" alt="封面">后',
    source => `/proxy?source=${source}`,
  )

  strictEqual(html, '前<img src="/proxy?source=/cover.png">后')
  doesNotMatch(html, /onerror|alt=/i)
})

test('任意非图片标签和注释都作为文本显示', () => {
  const html = renderSafeChapterHtml(
    '<a href="custom-action:payload">跳转</a><script>unexpected()</script>' +
      '<style>body{display:none}</style><svg><use href="#x"></use></svg>' +
      '<iframe src="/frame"></iframe><!-- hidden -->',
  )

  doesNotMatch(html, /<a\b|<script\b|<style\b|<svg\b|<iframe\b|<!--/i)
  match(html, /&lt;a href=&quot;custom-action:payload&quot;&gt;/)
  match(html, /&lt;script&gt;unexpected\(\)&lt;\/script&gt;/)
  match(html, /&lt;!-- hidden --&gt;/)
})

test('图片地址解析结果中的 HTML 特殊字符不会突破属性边界', () => {
  strictEqual(
    renderSafeChapterHtml('<img src="/cover.png">', () => `&<>"'`),
    '<img src="&amp;&lt;&gt;&quot;&#39;">',
  )
})

test('合法图片兼容单引号、空白、自闭合和一段多图', () => {
  const html = renderSafeChapterHtml(
    "甲<img\nsrc = '/a.png' alt='甲'>乙<img src=\"/b.png\" />丙",
  )

  strictEqual(html, '甲<img src="/a.png">乙<img src="/b.png">丙')
})

test('合法 Legado 图片请求参数在重建前保持完整', () => {
  const source =
    'https://example.com/cover.jpg,{"headers":{"User-Agent":"reader>browser","Cookie":"a=1"}}'
  const singleQuotedSource = `https://example.com/cover.jpg,{"headers":{"User-Agent":"reader \\"quoted\\"","Referer":"reader's"}}`

  strictEqual(getStandaloneImageSource(`<img src="${source}">`), source)
  strictEqual(
    renderSafeChapterHtml(`<img src="${source}">`),
    '<img src="https://example.com/cover.jpg,{&quot;headers&quot;:{&quot;User-Agent&quot;:&quot;reader&gt;browser&quot;,&quot;Cookie&quot;:&quot;a=1&quot;}}">',
  )
  strictEqual(
    getStandaloneImageSource(`<img src='${singleQuotedSource}'>`),
    singleQuotedSource,
  )
})

test('普通图片地址中的逗号和花括号不会被误判为请求参数', () => {
  for (const source of [
    'https://example.com/path,{}.jpg',
    'https://example.com/path,{literal.jpg',
  ]) {
    strictEqual(getStandaloneImageSource(`<img src="${source}">`), source)
  }
})

test('注释、原始文本标签和其他标签属性中的图片标记不触发图片请求', () => {
  const html = renderSafeChapterHtml(
    '<!--<img src="/comment.png">-->' +
      '<script>const value = \'<img src="/script.png">\'</script>' +
      '<style>.cover{content:\'<img src="/style.png">\'}</style>' +
      '<a title="<img src=\'/attribute.png\'>">链接</a>' +
      '<template><img src="/template.png"></template>' +
      '<noscript><img src="/noscript.png"></noscript>' +
      '<plaintext><img src="/plaintext.png"></plaintext>' +
      '<img src="/after-plaintext.png">',
  )

  doesNotMatch(html, /<img\b/i)
  match(html, /&lt;img src=&quot;\/comment\.png&quot;&gt;/)
  match(html, /&lt;img src=&quot;\/script\.png&quot;&gt;/)
  match(html, /&lt;img src=&#39;\/attribute\.png&#39;&gt;/)
})

test('未闭合标签属性中的图片标记不触发图片请求', () => {
  const html = renderSafeChapterHtml(`<a title="<img src='/activated.png'>`)

  strictEqual(
    html,
    '&lt;a title=&quot;&lt;img src=&#39;/activated.png&#39;&gt;',
  )
  doesNotMatch(html, /<img\b/i)
})

test('非标准或歧义图片标签全部按文本处理', () => {
  const cases = [
    [
      '<img-foo src="/cover.png">',
      '&lt;img-foo src=&quot;/cover.png&quot;&gt;',
    ],
    ['<IMG SRC="/cover.png">', '&lt;IMG SRC=&quot;/cover.png&quot;&gt;'],
    [
      '<img srcset="/cover.png 1x">',
      '&lt;img srcset=&quot;/cover.png 1x&quot;&gt;',
    ],
    [
      '<img data-src="/cover.png">',
      '&lt;img data-src=&quot;/cover.png&quot;&gt;',
    ],
    [
      '<img src="/first.png" src="/second.png">',
      '&lt;img src=&quot;/first.png&quot; src=&quot;/second.png&quot;&gt;',
    ],
    ['<img src="">', '&lt;img src=&quot;&quot;&gt;'],
    ['<img src="   ">', '&lt;img src=&quot;   &quot;&gt;'],
    ['<img src=/cover.png>', '&lt;img src=/cover.png&gt;'],
    ['<img alt="cover">', '&lt;img alt=&quot;cover&quot;&gt;'],
  ] as const

  for (const [input, expected] of cases) {
    strictEqual(renderSafeChapterHtml(input), expected)
  }
})

test('独立图片判断与安全渲染共用严格图片契约', () => {
  strictEqual(
    getStandaloneImageSource(" \n<img src='/cover.png' alt='封面' />\t"),
    '/cover.png',
  )
  strictEqual(getStandaloneImageSource('前<img src="/cover.png">'), undefined)
  strictEqual(
    getStandaloneImageSource('<img src="/a.png"><img src="/b.png">'),
    undefined,
  )
  strictEqual(
    getStandaloneImageSource('<img data-src="/cover.png">'),
    undefined,
  )
})

test('字数计算将每张合法图片计为一个原始字符', () => {
  strictEqual(
    calculateChapterWordCount('甲<img src="/a.png">乙<img src="/b.png">丙'),
    5,
  )

  const malformed = '<img data-src="/cover.png">'
  strictEqual(calculateChapterWordCount(malformed), malformed.length)

  const nonCanonical = '<IMG SRC="/cover.png">'
  strictEqual(calculateChapterWordCount(nonCanonical), nonCanonical.length)
})

test('大量未闭合请求参数保持线性可处理', () => {
  const malformed = '<img src="x,{'.repeat(12_000)
  const startedAt = performance.now()

  strictEqual(calculateChapterWordCount(malformed), malformed.length)
  const duration = performance.now() - startedAt
  ok(duration < 1_000, `解析耗时 ${duration.toFixed(1)}ms，疑似出现重复回扫`)
})

test('大量未闭合普通标记保持线性可处理', () => {
  const malformed = '<img src=x'.repeat(12_000) + '">'
  const startedAt = performance.now()

  strictEqual(calculateChapterWordCount(malformed), malformed.length)
  const duration = performance.now() - startedAt
  ok(duration < 1_000, `解析耗时 ${duration.toFixed(1)}ms，疑似出现重复回扫`)
})

test('大量未闭合原始文本结束标记保持线性可处理', () => {
  const malformed = '<script>' + '</script '.repeat(12_000)
  const startedAt = performance.now()

  strictEqual(calculateChapterWordCount(malformed), malformed.length)
  const duration = performance.now() - startedAt
  ok(duration < 1_000, `解析耗时 ${duration.toFixed(1)}ms，疑似出现重复回扫`)
})

test('每个图片元素最多尝试一次不同的代理回退地址', () => {
  const resolveFallback = createImageFallbackResolver(
    source => `/proxy?source=${source}`,
  )
  const image = {}

  strictEqual(resolveFallback(image, '/cover.png'), '/proxy?source=/cover.png')
  strictEqual(resolveFallback(image, '/cover.png'), undefined)

  const keepSameSource = createImageFallbackResolver(source => source)
  strictEqual(keepSameSource({}, '/already-proxied.png'), undefined)
})
