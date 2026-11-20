import { http } from './http'
import { getUserDetail, queryUsers, updateUserStatus } from './mock/adapter'
import type { AccountStatus, AdminUser, UserDetail, UserQuery } from './models'
import { IS_MOCK, mockCall } from './request'
import type { PageResult } from './types'

/** A02 用户管理数据服务 */
export async function fetchUsers(query: UserQuery): Promise<PageResult<AdminUser>> {
  if (IS_MOCK) {
    return mockCall(() => queryUsers(query))
  }
  return http.get<PageResult<AdminUser>>('/admin/users', {
    query: {
      keyword: query.keyword || undefined,
      userId: query.userId || undefined,
      role: query.role || undefined,
      status: query.status || undefined,
      binding: query.binding || undefined,
      page: query.page,
      pageSize: query.pageSize,
    },
  })
}

export async function fetchUserDetail(userId: string): Promise<UserDetail> {
  if (IS_MOCK) {
    return mockCall(() => getUserDetail(userId))
  }
  return http.get<UserDetail>(`/admin/users/${userId}`)
}

/** 启用 / 禁用账户；Mock 模式仅修改内存演示数据 */
export async function changeUserStatus(
  userId: string,
  status: AccountStatus,
): Promise<AdminUser> {
  if (IS_MOCK) {
    return mockCall(() => updateUserStatus(userId, status))
  }
  return http.patch<AdminUser>(`/admin/users/${userId}/status`, { status })
}
