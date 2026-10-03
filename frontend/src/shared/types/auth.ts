export type Role = 'manager' | 'guard'

export type AuthUser = { id: number; username: string; fullName: string; role: Role }

export type StaffAccount = AuthUser & { active: boolean }

export const roleLabel = (role: Role) => (role === 'manager' ? 'Quản lý vận hành' : 'Bảo vệ')

/** Trang chủ theo vai: quản lý vào Bảng điều khiển, bảo vệ vào màn nhiệm vụ. */
export const homeFor = (user: Pick<AuthUser, 'role'>) => (user.role === 'guard' ? '/bao-ve' : '/')
