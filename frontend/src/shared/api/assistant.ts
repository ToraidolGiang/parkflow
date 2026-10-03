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

export type AssistantContext = { role?: 'guard' | 'manager' }

export function chatWithAssistant(question: string, history: AssistantTurn[], signal?: AbortSignal, context?: AssistantContext) {
  return request<AssistantChatResponse>('/api/ai/chat', {
    method: 'POST',
    body: { question, history, ...context },
    signal,
  })
}
