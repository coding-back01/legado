import assert from 'node:assert/strict'
import test from 'node:test'

import {
  scanVueSource,
  verifyLinkInventory,
} from '../scripts/check-static-links.mjs'

const FILE = 'src/components/Fixture.vue'
const EXPECTED = [
  {
    file: FILE,
    text: '固定帮助',
    href: '/help/#fixed',
  },
]

function link(attributes, text = '固定帮助', tagName = 'el-link') {
  return `<template><${tagName} :icon="Link" ${attributes}>${text}</${tagName}></template>`
}

test('接受属性顺序无关的精确静态合同', () => {
  const links = scanVueSource(
    link('rel="noreferrer noopener" target="_blank" href="/help/#fixed"'),
    FILE,
  )
  assert.doesNotThrow(() => verifyLinkInventory(links, EXPECTED))
})

test('拒绝动态 href 绑定及 PascalCase 等价组件写法', () => {
  for (const source of [
    link(':href="runtimeUrl" target="_blank" rel="noopener noreferrer"'),
    link(
      'v-bind:href="runtimeUrl" target="_blank" rel="noopener noreferrer"',
      '固定帮助',
      'ElLink',
    ),
  ]) {
    assert.throws(() => scanVueSource(source, FILE), /未批准属性/)
  }
})

test('拒绝危险协议和未知固定目标', () => {
  const links = scanVueSource(
    link('href="javascript:alert(1)" target="_blank" rel="noopener noreferrer"'),
    FILE,
  )
  assert.throws(
    () => verifyLinkInventory(links, EXPECTED),
    /静态链接清单不一致/,
  )
})

test('拒绝数量漂移和重复链接', () => {
  const source = link(
    'href="/help/#fixed" target="_blank" rel="noopener noreferrer"',
  )
  const links = scanVueSource(`${source}${source}`, FILE)
  assert.throws(
    () => verifyLinkInventory(links, EXPECTED),
    /静态链接清单不一致/,
  )
})

test('拒绝缺失或重复的隔离属性', () => {
  assert.throws(
    () =>
      scanVueSource(
        link('href="/help/#fixed" target="_blank"'),
        FILE,
      ),
    /rel="noopener noreferrer"/,
  )
  assert.throws(
    () =>
      scanVueSource(
        link(
          'href="/help/#fixed" href="/help/#fixed" target="_blank" rel="noopener noreferrer"',
        ),
        FILE,
      ),
    /属性 href 重复出现/,
  )
})

test('拒绝复杂子节点和未闭合属性', () => {
  assert.throws(
    () =>
      scanVueSource(
        link(
          'href="/help/#fixed" target="_blank" rel="noopener noreferrer"',
          '<span>固定帮助</span>',
        ),
        FILE,
      ),
    /可见文字必须是纯文本/,
  )
  assert.throws(
    () => scanVueSource('<template><el-link href="unterminated>', FILE),
    /开始标签缺少闭合符号/,
  )
})
