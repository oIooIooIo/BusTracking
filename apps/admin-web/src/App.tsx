import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Checkbox, Col, Form, Input, Layout, Modal, Row, Select, Space, Statistic, Table, Tabs, Tag, Typography, message } from 'antd'
import { EditOutlined, EnvironmentOutlined, HistoryOutlined, LogoutOutlined, MobileOutlined, PlusOutlined, UserOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import 'leaflet/dist/leaflet.css'
import './App.css'
import RouteManagementPanel from './RouteManagementPanel'
import RouteHistoryPanel from './RouteHistoryPanel'
import BoardingEventsPanel from './BoardingEventsPanel'
import { api, type Bus, type Device, type DeviceAssignment, type Employee, type Route } from './api'

const { Header, Content } = Layout
type Credentials = { username: string; password: string }
type Client = ReturnType<typeof api>

function App() {
  const [credentials, setCredentials] = useState<Credentials | null>(null)
  return credentials ? <AdminConsole credentials={credentials} onLogout={() => setCredentials(null)} /> : <Login onLogin={setCredentials} />
}

function Login({ onLogin }: { onLogin: (value: Credentials) => void }) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const submit = async (values: Credentials) => {
    setLoading(true)
    try { await api(values).buses(); onLogin(values) } catch (cause) { setError(cause instanceof Error ? cause.message : 'Sign in failed') } finally { setLoading(false) }
  }
  return <div className="login-page"><Card className="login-card"><Typography.Title level={2}>Bus Tracking Admin</Typography.Title>{error && <Alert type="error" message={error} showIcon />}<Form layout="vertical" onFinish={submit} initialValues={{ username: 'admin', password: 'admin123' }}><Form.Item name="username" label="Username" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="password" label="Password" rules={[{ required: true }]}><Input.Password /></Form.Item><Button type="primary" htmlType="submit" loading={loading} block>Sign in</Button></Form></Card></div>
}

function AdminConsole({ credentials, onLogout }: { credentials: Credentials; onLogout: () => void }) {
  const client = useMemo(() => api(credentials), [credentials])
  const [buses, setBuses] = useState<Bus[]>([])
  const [devices, setDevices] = useState<Device[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [routes, setRoutes] = useState<Route[]>([])
  const [selectedBusId, setSelectedBusId] = useState<string>()
  const [error, setError] = useState('')
  const reload = useCallback(async () => {
    try {
      const [busData, deviceData, employeeData, routeData] = await Promise.all([client.buses(), client.devices(), client.employees(), client.routes()])
      setBuses(busData); setDevices(deviceData); setEmployees(employeeData); setRoutes(routeData)
      setSelectedBusId(current => current ?? busData[0]?.id); setError('')
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Failed to load data') }
  }, [client])
  useEffect(() => {
    // Remote data must load when the authenticated client changes.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void reload()
  }, [reload])
  return <Layout className="app-shell"><Header className="app-header"><Space><EnvironmentOutlined className="brand-icon" /><Typography.Title level={3} className="brand-title">Bus Tracking Admin</Typography.Title></Space><Button icon={<LogoutOutlined />} onClick={onLogout}>Logout</Button></Header><Content className="app-content">{error && <Alert type="error" message={error} showIcon closable />}<Row gutter={16} className="summary-row"><Col xs={24} md={8}><Card><Statistic title="Buses" value={buses.length} /></Card></Col><Col xs={24} md={8}><Card><Statistic title="Devices" value={devices.length} /></Card></Col><Col xs={24} md={8}><Card><Statistic title="Employees" value={employees.length} /></Card></Col></Row><Tabs items={[
    { key: 'buses', label: 'Buses', children: <BusPanel client={client} buses={buses} routes={routes} reload={reload} /> },
    { key: 'routes', label: 'Routes', children: <RouteManagementPanel client={client} routes={routes} employees={employees} reload={reload} /> },
    { key: 'employees', label: 'Employees', children: <EmployeePanel client={client} employees={employees} reload={reload} /> },
    { key: 'devices', label: 'Devices', children: <DevicePanel client={client} buses={buses} devices={devices} reload={reload} /> },
    { key: 'route', label: 'Bus history', children: <RouteHistoryPanel client={client} buses={buses} selectedBusId={selectedBusId} setSelectedBusId={setSelectedBusId} /> },
    { key: 'events', label: 'Boarding events', children: <BoardingEventsPanel client={client} buses={buses} selectedBusId={selectedBusId} setSelectedBusId={setSelectedBusId} /> },
  ]} /></Content></Layout>
}

function BusPanel({ client, buses, routes, reload }: { client: Client; buses: Bus[]; routes: Route[]; reload: () => Promise<void> }) {
  const [editing, setEditing] = useState<Bus | null | undefined>()
  return <Card><Space className="toolbar"><Button icon={<PlusOutlined />} onClick={() => setEditing(null)}>Add bus</Button></Space><Table rowKey="id" dataSource={buses} pagination={false} columns={[
    { title: 'Bus code', dataIndex: 'code' }, { title: 'Display name', dataIndex: 'name' },
    { title: 'Installed device', render: (_, bus) => bus.installedDeviceCode ? `${bus.installedDeviceCode} / ${bus.installedHardwareSerial}` : <Tag color="orange">Not installed</Tag> },
    { title: 'Status', dataIndex: 'active', render: active => active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag> },
    { title: 'Routes', render: (_, bus) => <BusRouteAssignment client={client} bus={bus} routes={routes} reload={reload} /> },
    { title: 'Config sync', render: (_, bus) => bus.configurationSynced ? <Tag color="green">Synced v{bus.appliedConfigurationVersion}</Tag> : <Tag color="orange">Waiting: desired v{bus.desiredConfigurationVersion}, device v{bus.appliedConfigurationVersion ?? 'none'}</Tag> },
    { title: 'Action', render: (_, bus) => <Button type="link" icon={<EditOutlined />} onClick={() => setEditing(bus)}>Edit</Button> },
  ]} /><BusModal open={editing !== undefined} bus={editing ?? null} client={client} reload={reload} onClose={() => setEditing(undefined)} /></Card>
}

function BusRouteAssignment({ client, bus, routes, reload }: { client: Client; bus: Bus; routes: Route[]; reload: () => Promise<void> }) {
  const [saving, setSaving] = useState(false)
  const assignedIds = bus.routes.map(route => route.id)
  const save = async (routeIds: string[]) => {
    setSaving(true)
    try { await client.assignRoutes(bus.id, routeIds); await reload(); message.success('Bus routes updated') }
    catch (cause) { message.error(cause instanceof Error ? cause.message : 'Unable to update routes') }
    finally { setSaving(false) }
  }
  const change = (routeIds: string[]) => {
    if (assignedIds.some(routeId => !routeIds.includes(routeId))) return
    void save(routeIds)
  }
  const confirmRemoval = (routeId: string) => {
    const route = routes.find(item => item.id === routeId)
    if (!route) return
    Modal.confirm({
      title: 'Remove route from bus?',
      content: `Are you sure you want to remove "${route.code} - ${route.name}" from "${bus.code}"?`,
      okText: 'Remove', okButtonProps: { danger: true }, cancelText: 'Cancel',
      onOk: () => save(assignedIds.filter(id => id !== routeId)),
    })
  }
  return <Select mode="multiple" loading={saving} value={assignedIds} onChange={change} tagRender={({ label, value, closable }) => <Tag closable={closable} onMouseDown={event => { event.preventDefault(); event.stopPropagation() }} onClose={event => { event.preventDefault(); event.stopPropagation(); confirmRemoval(String(value)) }}>{label}</Tag>} style={{ minWidth: 240 }} options={routes.filter(route => route.active).map(route => ({ value: route.id, label: route.code + ' - ' + route.name }))} placeholder="No active routes" />
}

function BusModal({ open, bus, client, reload, onClose }: { open: boolean; bus: Bus | null; client: Client; reload: () => Promise<void>; onClose: () => void }) {
  const [form] = Form.useForm(); const [saving, setSaving] = useState(false)
  useEffect(() => { if (open) form.setFieldsValue(bus ? { code: bus.code, name: bus.name, active: bus.active, clearPermissions: false } : { code: '', name: '', active: true }) }, [open, bus, form])
  const save = async (values: { code: string; name: string; active: boolean; clearPermissions?: boolean }) => {
    setSaving(true)
    try {
      if (bus) await client.updateBus(bus.id, { code: values.code, name: values.name, active: values.active, clearPermissions: values.clearPermissions ?? false })
      else await client.createBus({ code: values.code, name: values.name, active: values.active })
      await reload(); message.success(bus ? 'Bus updated' : 'Bus added'); onClose()
    } catch (cause) { message.error(cause instanceof Error ? cause.message : 'Unable to save bus') } finally { setSaving(false) }
  }
  const activeValue = Form.useWatch('active', form)
  const reactivating = Boolean(bus && !bus.active && activeValue)
  return <Modal open={open} title={bus ? 'Edit bus' : 'Add bus'} onCancel={onClose} onOk={() => form.submit()} confirmLoading={saving} destroyOnHidden><Form form={form} layout="vertical" onFinish={save}><Form.Item name="code" label="Bus code" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="name" label="Display name" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="active" valuePropName="checked"><Checkbox>Active</Checkbox></Form.Item>{reactivating && bus && bus.permissionCount > 0 && <Alert type="warning" showIcon message={`This bus has ${bus.permissionCount} existing permissions`} description={<Form.Item name="clearPermissions" valuePropName="checked" noStyle><Checkbox>Clear existing permissions when reactivating</Checkbox></Form.Item>} />}</Form></Modal>
}

function DevicePanel({ client, buses, devices, reload }: { client: Client; buses: Bus[]; devices: Device[]; reload: () => Promise<void> }) {
  const [editing, setEditing] = useState<Device | null | undefined>(); const [historyDevice, setHistoryDevice] = useState<Device>()
  return <Card><Alert className="toolbar" type="info" showIcon message="Before moving a device, sync pending offline data, deactivate it, reassign it, then activate it on the new bus." /><Button className="toolbar" icon={<MobileOutlined />} onClick={() => setEditing(null)}>Register device</Button><Table rowKey="id" dataSource={devices} pagination={false} columns={[
    { title: 'Device code', dataIndex: 'deviceCode' }, { title: 'Hardware serial', dataIndex: 'hardwareSerial' }, { title: 'Bus', dataIndex: 'busCode', render: value => value ?? <Tag color="orange">Unassigned</Tag> },
    { title: 'Status', dataIndex: 'active', render: active => active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag> },
    { title: 'Last seen', dataIndex: 'lastSeenAt', render: value => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : 'Never' },
    { title: 'Action', render: (_, device) => <Space><Button type="link" icon={<EditOutlined />} onClick={() => setEditing(device)}>Edit</Button><Button type="link" icon={<HistoryOutlined />} onClick={() => setHistoryDevice(device)}>History</Button></Space> },
  ]} /><DeviceModal open={editing !== undefined} device={editing ?? null} client={client} buses={buses} reload={reload} onClose={() => setEditing(undefined)} />{historyDevice && <AssignmentHistoryModal device={historyDevice} client={client} onClose={() => setHistoryDevice(undefined)} />}</Card>
}

function DeviceModal({ open, device, client, buses, reload, onClose }: { open: boolean; device: Device | null; client: Client; buses: Bus[]; reload: () => Promise<void>; onClose: () => void }) {
  const [form] = Form.useForm(); const [saving, setSaving] = useState(false)
  useEffect(() => { if (open) form.setFieldsValue(device ? { hardwareSerial: device.hardwareSerial, busId: device.busId, active: device.active } : { hardwareSerial: '', busId: undefined, active: false }) }, [open, device, form])
  const selectedBusId = Form.useWatch('busId', form)
  const save = async (values: Omit<Device, 'id' | 'deviceCode' | 'busCode' | 'lastSeenAt'>) => { setSaving(true); try { const input = { ...values, busId: values.busId || undefined, active: Boolean(values.busId && values.active) }; if (device) await client.updateDevice(device.id, input); else await client.createDevice(input); await reload(); message.success(device ? 'Device updated' : 'Device registered'); onClose() } catch (cause) { message.error(cause instanceof Error ? cause.message : 'Unable to save device') } finally { setSaving(false) } }
  return <Modal open={open} title={device ? 'Edit device' : 'Register device'} onCancel={onClose} onOk={() => form.submit()} confirmLoading={saving} destroyOnHidden><Form form={form} layout="vertical" onFinish={save}>{device && <Form.Item label="Device code"><Input value={device.deviceCode} readOnly /></Form.Item>}<Form.Item name="hardwareSerial" label="Hardware serial" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="busId" label="Bus"><Select allowClear placeholder="Unassigned" onClear={() => form.setFieldValue('active', false)} options={buses.filter(bus => bus.active).map(bus => ({ value: bus.id, label: `${bus.code} - ${bus.name}` }))} /></Form.Item><Form.Item name="active" valuePropName="checked"><Checkbox disabled={!selectedBusId}>Active</Checkbox></Form.Item></Form></Modal>
}

function AssignmentHistoryModal({ device, client, onClose }: { device: Device; client: Client; onClose: () => void }) {
  const [rows, setRows] = useState<DeviceAssignment[]>([])
  useEffect(() => { void client.assignmentHistory(device.id).then(setRows).catch(cause => message.error(cause instanceof Error ? cause.message : 'Unable to load history')) }, [client, device.id])
  return <Modal open title={`Assignment history - ${device.deviceCode}`} footer={null} onCancel={onClose} width={760}><Table rowKey="id" dataSource={rows} pagination={false} columns={[{ title: 'Bus', render: (_, row) => `${row.busCode} - ${row.busName}` }, { title: 'Installed at', dataIndex: 'installedAt', render: value => dayjs(value).format('YYYY-MM-DD HH:mm:ss') }, { title: 'Removed at', dataIndex: 'removedAt', render: value => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : <Tag color="green">Current</Tag> }]} /></Modal>
}

function EmployeePanel({ client, employees, reload }: { client: Client; employees: Employee[]; reload: () => Promise<void> }) {
  const [query, setQuery] = useState(''); const [editing, setEditing] = useState<Employee | null | undefined>()
  const filtered = employees.filter(employee => employee.employeeNo.toLowerCase().includes(query.trim().toLowerCase()))
  return <Card><Space className="toolbar" wrap><Button icon={<UserOutlined />} onClick={() => setEditing(null)}>Add employee</Button><Input.Search allowClear placeholder="Search employee number" onSearch={setQuery} onChange={event => setQuery(event.target.value)} style={{ width: 300 }} /></Space><Table rowKey="id" dataSource={filtered} pagination={{ pageSize: 20 }} columns={[
    { title: 'Employee number', dataIndex: 'employeeNo' }, { title: 'Name', dataIndex: 'name' }, { title: 'Department', dataIndex: 'department' }, { title: 'CardSN', dataIndex: 'cardSn' },
    { title: 'Status', dataIndex: 'active', render: active => active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag> },
    { title: 'Action', render: (_, employee) => <Button type="link" icon={<EditOutlined />} onClick={() => setEditing(employee)}>Edit</Button> },
  ]} /><EmployeeModal open={editing !== undefined} employee={editing ?? null} client={client} reload={reload} onClose={() => setEditing(undefined)} /></Card>
}

function EmployeeModal({ open, employee, client, reload, onClose }: { open: boolean; employee: Employee | null; client: Client; reload: () => Promise<void>; onClose: () => void }) {
  const [form] = Form.useForm(); const [saving, setSaving] = useState(false)
  useEffect(() => { if (open) form.setFieldsValue(employee ?? { employeeNo: '', name: '', department: '', cardSn: '', active: true }) }, [open, employee, form])
  const save = async (values: Omit<Employee, 'id'>) => { setSaving(true); try { if (employee) await client.updateEmployee(employee.id, values); else await client.createEmployee(values); await reload(); message.success(employee ? 'Employee updated' : 'Employee added'); onClose() } catch (cause) { message.error(cause instanceof Error ? cause.message : 'Unable to save employee') } finally { setSaving(false) } }
  return <Modal open={open} title={employee ? 'Edit employee' : 'Add employee'} onCancel={onClose} onOk={() => form.submit()} confirmLoading={saving} destroyOnHidden><Form form={form} layout="vertical" onFinish={save}><Form.Item name="employeeNo" label="Employee number" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="name" label="Name" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="department" label="Department" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="cardSn" label="CardSN" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="active" valuePropName="checked"><Checkbox>Active</Checkbox></Form.Item></Form></Modal>
}

export default App
