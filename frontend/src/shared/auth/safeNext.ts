/** Chỉ cho quay lại đường dẫn nội bộ (chặn `//evil.com` và `https://...`) sau khi đăng nhập. */
export const safeNext = (next: string | null, fallback: string) =>
  next && next.startsWith('/') && !next.startsWith('//') && !next.startsWith('/dang-nhap') ? next : fallback
