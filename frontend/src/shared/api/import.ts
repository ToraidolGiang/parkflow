import mockMapping from '../mock/map-columns.json'
import type { MappingInput, MappingResponse, MappingResult } from '../types/import'
import { mockApiEnabled, request } from './http'

export async function mapColumns(input: MappingInput, signal?: AbortSignal): Promise<MappingResult> {
  if (mockApiEnabled()) {
    const result = mockMapping as MappingResponse
    return {
      ...result,
      mappings: result.mappings.filter((mapping) => input.headers.includes(mapping.column)),
      fallback: true,
    }
  }

  try {
    const result = await request<MappingResponse, MappingResponse>('/api/ai/map-columns', {
      method: 'POST',
      body: input,
      signal,
    })
    return { ...result, fallback: result.source === 'mock' }
  } catch (error) {
    const apiError = error as { status?: number; fallback?: MappingResponse }
    if (apiError.status === 503 && apiError.fallback) {
      return { ...apiError.fallback, fallback: true }
    }
    throw error
  }
}
