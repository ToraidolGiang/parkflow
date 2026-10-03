import { useLocation } from 'react-router-dom'
import { navigationItems } from '../routes'

export function PlaceholderPage() {
  const { pathname } = useLocation()
  const item = navigationItems.find((entry) => entry.path === pathname)

  return (
    <section className="page placeholder-page">
      <header className="page-header">
        <h1>{item?.label ?? 'CampusPark'}</h1>
        <p>Màn hình này nằm ngoài phạm vi triển khai ngày 29/09.</p>
      </header>
      <article className="empty-card">
        <strong>Khung route đã sẵn sàng</strong>
        <p>Trang sẽ được triển khai ở task tiếp theo theo thiết kế Figma.</p>
      </article>
    </section>
  )
}
