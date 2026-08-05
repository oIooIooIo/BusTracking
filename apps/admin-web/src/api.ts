export type RouteSummary = { id: string; code: string; name: string }
export type Stop = { id: string; code: string; name: string; latitude: number; longitude: number; radiusMeters: number; active: boolean; routeCount: number; order?: number }
export type StopInput = { name: string; latitude: number; longitude: number; radiusMeters: number; active: boolean }
export type RouteInput = { code: string; name: string; active: boolean; stopIds: string[] }
export type Route = RouteSummary & { active: boolean; permissionVersion: number; employeeCount: number; busCount: number; stops: Stop[] }
export type RouteImportIssue = { rowNumber: number; severity: 'ERROR' | 'WARNING'; code: string; message: string; employeeNo?: string; routeCode?: string }
export type RouteImportResult = {
  committed: boolean
  totalRows: number
  validPermissionRows: number
  skippedRows: number
  newRouteCount: number
  existingRouteCount: number
  newPermissionCount: number
  existingPermissionCount: number
  errorCount: number
  warningCount: number
  issues: RouteImportIssue[]
}
export type Bus = { id: string; code: string; name: string; installedDeviceCode?: string; installedHardwareSerial?: string; active: boolean; permissionVersion: number; permissionCount: number; routes: RouteSummary[]; desiredConfigurationVersion: number; appliedConfigurationVersion?: number; configurationSynced: boolean; configurationAppliedAt?: string }
export type Employee = { id: string; employeeNo: string; name: string; department: string; cardSn: string; active: boolean }
export type Device = { id: string; deviceCode: string; hardwareSerial: string; busId?: string; busCode?: string; active: boolean; lastSeenAt?: string }
export type DeviceAssignment = { id: string; deviceId: string; busId: string; busCode: string; busName: string; installedAt: string; removedAt?: string }
export type RoutePoint = { recordedAt: string; latitude: number; longitude: number; accuracyMeters?: number }
export type RouteHistory = { busId: string; from: string; to: string; dailyMileageKm: number; points: RoutePoint[] }
export type BoardingEvent = { id: string; employeeId?: string; employeeNo?: string; employeeName?: string; employeeDepartment?: string; cardSn: string; result: string; eventType: string; scannedAt: string; permissionVersion?: number; busId: string; deviceId: string; routes: RouteSummary[]; stopId?: string; stopName?: string; latitude?: number; longitude?: number; locationRecordedAt?: string; locationSource: string; accuracyMeters?: number }

type Credentials = { username: string; password: string }
const baseUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/admin/v1'

export function api(credentials: Credentials) {
  const request = async <T>(path: string, init?: RequestInit): Promise<T> => {
    const headers = new Headers(init?.headers)
    headers.set('Authorization', `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`)
    if (!(init?.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    const response = await fetch(`${baseUrl}${path}`, {
      ...init,
      headers,
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
    routes: () => request<Route[]>('/routes'),
    stops: (q?: string, active?: boolean) => {
      const params = new URLSearchParams()
      if (q) params.set('q', q)
      if (active !== undefined) params.set('active', String(active))
      return request<Stop[]>(`/stops?${params}`)
    },
    createStop: (input: StopInput) => request<Stop>('/stops', { method: 'POST', body: JSON.stringify(input) }),
    updateStop: (id: string, input: StopInput) => request<Stop>('/stops/' + id, { method: 'PUT', body: JSON.stringify(input) }),
    stopRoutes: (id: string) => request<RouteSummary[]>('/stops/' + id + '/routes'),
    createRoute: (input: RouteInput) => request<Route>('/routes', { method: 'POST', body: JSON.stringify(input) }),
    updateRoute: (id: string, input: RouteInput) => request<Route>('/routes/' + id, { method: 'PUT', body: JSON.stringify(input) }),
    previewRouteImport: (file: File) => {
      const body = new FormData()
      body.append('file', file)
      return request<RouteImportResult>('/routes/import/preview', { method: 'POST', body })
    },
    importRoutes: (file: File) => {
      const body = new FormData()
      body.append('file', file)
      return request<RouteImportResult>('/routes/import', { method: 'POST', body })
    },
    routePermissions: (routeId: string) => request<Employee[]>('/routes/' + routeId + '/permissions'),
    grantRoutePermissions: (routeId: string, employeeIds: string[]) => request<void>('/routes/' + routeId + '/permissions/batch', { method: 'POST', body: JSON.stringify({ employeeIds }) }),
    revokeRoutePermission: (routeId: string, employeeId: string) => request<void>('/routes/' + routeId + '/permissions/' + employeeId, { method: 'DELETE' }),
    assignedRoutes: (busId: string) => request<string[]>('/buses/' + busId + '/routes'),
    assignRoutes: (busId: string, routeIds: string[]) => request<void>('/buses/' + busId + '/routes', { method: 'PUT', body: JSON.stringify({ routeIds }) }),
    createBus: (input: { code: string; name: string; active: boolean }) => request<Bus>('/buses', { method: 'POST', body: JSON.stringify(input) }),
    updateBus: (id: string, input: { code: string; name: string; active: boolean; clearPermissions: boolean }) => request<Bus>(`/buses/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
    devices: () => request<Device[]>('/devices'),
    createDevice: (input: Omit<Device, 'id' | 'deviceCode' | 'busCode' | 'lastSeenAt'>) => request<Device>('/devices', { method: 'POST', body: JSON.stringify(input) }),
    updateDevice: (id: string, input: Omit<Device, 'id' | 'deviceCode' | 'busCode' | 'lastSeenAt'>) => request<Device>(`/devices/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
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
