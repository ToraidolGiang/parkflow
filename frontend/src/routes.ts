export type NavigationItem = {
  label: string
  path: string
  icon: 'assistant' | 'import' | 'dashboard' | 'forecast' | 'rules' | 'settings'
}

export const navigationItems: NavigationItem[] = [
  { label: 'Trợ lý', path: '/assistant', icon: 'assistant' },
  { label: 'Nhập dữ liệu', path: '/import', icon: 'import' },
  { label: 'Bảng điều khiển', path: '/', icon: 'dashboard' },
  { label: 'Dự báo & Đề xuất', path: '/forecast', icon: 'forecast' },
  { label: 'Quy tắc vận hành', path: '/rules', icon: 'rules' },
  { label: 'Cấu hình trường', path: '/settings', icon: 'settings' },
]
