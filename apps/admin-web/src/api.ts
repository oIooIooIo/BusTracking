export type Bus = { id: string; code: string; name: string; hardwareSerial?: string; active: boolean; permissionVersion: number; permissionCount: number }
export type Employee = { id: string; employeeNo: string; name: string; cardSn: string; active: boolean }
export type Device = { id: string; deviceCode: string; hardwareSerial: string; busId: string; busCode: string; active: boolean; lastSeenAt?: string }
export type DeviceAssignment = { id: string; deviceId: string; busId: string; busCode: string; busName: string; installedAt: string; removedAt?: string }
export type RoutePoint = { recordedAt: string; latitude: number; longitude: number; accuracyMeters?: number }
export type RouteHistory = { busId: string; from: string; to: string; points: RoutePoint[] }
export type BoardingEvent = { id: string; employeeId?: string; cardSn: string; result: string; scannedAt: string; permissionVersion?: number }

type Credentials = { username: string; password: string }
const baseUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/admin/v1'

export function api(credentials: Credentials) {
  const request = async <T>(path: string, init?: RequestInit): Promise<T> => {
    const response = await fetch(`${baseUrl}${path}`, {
      ...init,
      headers: { Authorization: `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`, 'Content-Type': 'application/json', ...init?.headers },
    })
    if (!response.ok) {
      const body = await response.json().catch(() => ({ message: response.statusText }))
      throw new Error(body.message ?? `Request failed: ${response.status}`)
    }
    if (response.status === 204) return undefined as T
    return response.json() as Promise<T>
  }
  return {
    buses: () => request<Bus[]>('/buses'),
    createBus: (input: { code: string; name: string; hardwareSerial: string; active: boolean }) => request<Bus>('/buses', { method: 'POST', body: JSON.stringify(input) }),
    updateBus: (id: string, input: { code: string; name: string; active: boolean; clearPermissions: boolean }) => request<Bus>(`/buses/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
    devices: () => request<Device[]>('/devices'),
    createDevice: (input: Omit<Device, 'id' | 'busCode' | 'lastSeenAt'>) => request<Device>('/devices', { method: 'POST', body: JSON.stringify(input) }),
    updateDevice: (id: string, input: Omit<Device, 'id' | 'busCode' | 'lastSeenAt'>) => request<Device>(`/devices/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
    assignmentHistory: (id: string) => request<DeviceAssignment[]>(`/devices/${id}/assignment-history`),
    employees: (employeeNo?: string) => request<Employee[]>(`/employees${employeeNo ? `?employeeNo=${encodeURIComponent(employeeNo)}` : ''}`),
    createEmployee: (input: Omit<Employee, 'id'>) => request<Employee>('/employees', { method: 'POST', body: JSON.stringify(input) }),
    updateEmployee: (id: string, input: Omit<Employee, 'id'>) => request<Employee>(`/employees/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
    permissions: (busId: string) => request<Employee[]>(`/buses/${busId}/permissions`),
    grantBatch: (busId: string, employeeIds: string[]) => request<void>(`/buses/${busId}/permissions/batch`, { method: 'POST', body: JSON.stringify({ employeeIds }) }),
    revoke: (busId: string, employeeId: string) => request<void>(`/buses/${busId}/permissions/${employeeId}`, { method: 'DELETE' }),
    route: (busId: string, from: string, to: string) => request<RouteHistory>(`/buses/${busId}/gps-points?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
    events: (busId: string, from: string, to: string) => request<BoardingEvent[]>(`/buses/${busId}/boarding-events?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  }
}
