const GOOGLE_MAPS_HOSTS = new Set(['google.com', 'www.google.com', 'maps.google.com'])

export function isSafeMapsUrl(value: string) {
  try {
    const url = new URL(value)
    const routePath = url.pathname === '/maps/dir' || url.pathname.startsWith('/maps/dir/')
    return url.protocol === 'https:'
      && !url.username
      && !url.password
      && GOOGLE_MAPS_HOSTS.has(url.hostname)
      && routePath
  } catch {
    return false
  }
}
