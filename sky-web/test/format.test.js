import test from 'node:test'
import assert from 'node:assert/strict'
import { money, flavorOptions, imageUrl, text, dateTime } from '../src/format.js'

test('金额仅展示有效非负数字，不暴露NaN或空金额', () => {
  assert.equal(money('28'), '28.00')
  assert.equal(money(0), '0.00')
  for (const value of [undefined, null, {}, [], true, '', ' ', 'NaN', Infinity, -1]) assert.equal(money(value), '—')
})

test('口味兼容JSON和历史分隔格式，去除非法选项并保持顺序', () => {
  assert.deepEqual(flavorOptions('["不辣","微辣","微辣",null,{}]'), ['不辣', '微辣'])
  assert.deepEqual(flavorOptions('不辣，微辣、中辣'), ['不辣', '微辣', '中辣'])
  assert.deepEqual(flavorOptions(null), [])
})

test('图片回退和文字格式避免非法协议或object文本', () => {
  assert.equal(imageUrl('javascript:alert(1)'), '/images/dish-placeholder.svg')
  assert.equal(imageUrl('//untrusted.example/a.png'), '/images/dish-placeholder.svg')
  assert.equal(imageUrl('/images/dish-placeholder.svg'), '/images/dish-placeholder.svg')
  assert.equal(text({ message: 'invalid' }, '暂时不可用'), '暂时不可用')
  assert.equal(dateTime('not-date'), '时间待更新')
})
