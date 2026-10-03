import { afterEach, describe, expect, it, vi } from 'vitest'
import { chatWithAssistant, type AssistantTurn } from './assistant'

describe('assistant chat api', () => {
  afterEach(() => vi.restoreAllMocks())

  it('posts the question and history to the backend chat contract', async () => {
    const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      answer: 'Bãi C còn phù hợp.', facts: [{ label: 'Bãi C', value: '67%' }], suggestions: ['Câu hỏi tiếp theo'], source: 'mock',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    const history: AssistantTurn[] = [{ role: 'user', content: 'Câu hỏi trước' }]

    await expect(chatWithAssistant('Bãi nào còn chỗ?', history)).resolves.toMatchObject({ source: 'mock' })
    expect(fetcher).toHaveBeenCalledWith('/api/ai/chat', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ question: 'Bãi nào còn chỗ?', history }),
    }))
  })
})
