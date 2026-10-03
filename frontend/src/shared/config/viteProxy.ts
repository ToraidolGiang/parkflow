const DEFAULT_API_PROXY_TARGET = 'http://localhost:8080'

export function resolveApiProxyTarget(env: Record<string, string | undefined>) {
  return env.VITE_API_PROXY_TARGET?.trim() || DEFAULT_API_PROXY_TARGET
}
