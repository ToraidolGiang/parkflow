import { request } from './http'

export type AssistantTurn = { role: 'user' | 'assistant'; content: string }
export type AssistantFact = { label: string; value: string }
export type AssistantSource = 'gemini' | 'groq' | 'mock'
export type AssistantChatResponse = {
  answer: string
  facts: AssistantFact[]
  suggestions: string[]
  source: AssistantSource
}

export function chatWithAssistant(question: string, history: AssistantTurn[], signal?: AbortSignal) {
  return request<AssistantChatResponse>('/api/ai/chat', {
    method: 'POST',
    body: { question, history },
    signal,
  })
}
