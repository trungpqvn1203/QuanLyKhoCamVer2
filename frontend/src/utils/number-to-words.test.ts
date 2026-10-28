import { describe, expect, it } from 'vitest'
import { vndToWords } from './number-to-words'

describe('vndToWords', () => {
  it.each([
    [0, 'Không đồng'],
    [5, 'Năm đồng'],
    [10, 'Mười đồng'],
    [11, 'Mười một đồng'],
    [15, 'Mười lăm đồng'],
    [21, 'Hai mươi mốt đồng'],
    [24, 'Hai mươi tư đồng'],
    [25, 'Hai mươi lăm đồng'],
    [100, 'Một trăm đồng'],
    [105, 'Một trăm linh năm đồng'],
    [110, 'Một trăm mười đồng'],
    [1_005, 'Một nghìn không trăm linh năm đồng'],
    [1_050, 'Một nghìn không trăm năm mươi đồng'],
    [320_000, 'Ba trăm hai mươi nghìn đồng'],
    [1_000_005, 'Một triệu không trăm linh năm đồng'],
    [5_250_000, 'Năm triệu hai trăm năm mươi nghìn đồng'],
    [16_000_000, 'Mười sáu triệu đồng'],
    [1_000_000_000, 'Một tỷ đồng'],
    [2_000_001_000, 'Hai tỷ không trăm linh một nghìn đồng'],
    [2_500_000_000_000, 'Hai nghìn năm trăm tỷ đồng'],
  ])('%d → %s', (amount, words) => {
    expect(vndToWords(amount)).toBe(words)
  })

  it('từ chối số âm hoặc số lẻ', () => {
    expect(() => vndToWords(-1)).toThrow(RangeError)
    expect(() => vndToWords(12.5)).toThrow(RangeError)
  })
})
