import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Checkbox, Col, DatePicker, Form, Input, Layout, List, Modal, Row, Select, Space, Statistic, Table, Tabs, Tag, Typography, message } from 'antd'
import { EditOutlined, EnvironmentOutlined, HistoryOutlined, IdcardOutlined, LogoutOutlined, MobileOutlined, PlusOutlined, UserOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { MapContainer, Polyline, TileLayer } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import './App.css'
import { api, type BoardingEvent, type Bus, type Device, type DeviceAssignment, type Employee, type RouteHistory } from './api'

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
  const [selectedBusId, setSelectedBusId] = useState<string>()
  const [error, setError] = useState('')
  const reload = useCallback(async () => {
    try {
      const [busData, deviceData, employeeData] = await Promise.all([client.buses(), client.devices(), client.employees()])
      setBuses(busData); setDevices(deviceData); setEmployees(employeeData)
      setSelectedBusId(current => current ?? busData[0]?.id); setError('')
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Failed to load data') }
  }, [client])
  useEffect(() => {
    // Remote data must load when the authenticated client changes.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void reload()
  }, [reload])
  return <Layout className="app-shell"><Header className="app-header"><Space><EnvironmentOutlined className="brand-icon" /><Typography.Title level={3} className="brand-title">Bus Tracking Admin</Typography.Title></Space><Button icon={<LogoutOutlined />} onClick={onLogout}>Logout</Button></Header><Content className="app-content">{error && <Alert type="error" message={error} showIcon closable />}<Row gutter={16} className="summary-row"><Col xs={24} md={8}><Card><Statistic title="Buses" value={buses.length} /></Card></Col><Col xs={24} md={8}><Card><Statistic title="Devices" value={devices.length} /></Card></Col><Col xs={24} md={8}><Card><Statistic title="Employees" value={employees.length} /></Card></Col></Row><Tabs items={[
    { key: 'buses', label: 'Buses', children: <BusPanel client={client} buses={buses} reload={reload} /> },
    { key: 'permissions', label: 'Boarding permissions', children: <PermissionPanel client={client} buses={buses} employees={employees} selectedBusId={selectedBusId} setSelectedBusId={setSelectedBusId} reload={reload} /> },
    { key: 'employees', label: 'Employees', children: <EmployeePanel client={client} employees={employees} reload={reload} /> },
    { key: 'devices', label: 'Devices', children: <DevicePanel client={client} buses={buses} devices={devices} reload={reload} /> },
    { key: 'route', label: 'Route history', children: <RoutePanel client={client} buses={buses} selectedBusId={selectedBusId} setSelectedBusId={setSelectedBusId} /> },
    { key: 'events', label: 'Boarding events', children: <EventPanel client={client} buses={buses} selectedBusId={selectedBusId} setSelectedBusId={setSelectedBusId} /> },
  ]} /></Content></Layout>
}

function BusPanel({ client, buses, reload }: { client: Client; buses: Bus[]; reload: () => Promise<void> }) {
  const [editing, setEditing] = useState<Bus | null | undefined>()
  return <Card><Space className="toolbar"><Button icon={<PlusOutlined />} onClick={() => setEditing(null)}>Add bus</Button></Space><Table rowKey="id" dataSource={buses} pagination={false} columns={[
    { title: 'Bus code', dataIndex: 'code' }, { title: 'Display name', dataIndex: 'name' },
    { title: 'Hardware serial', dataIndex: 'hardwareSerial', render: value => value ?? <Tag color="orange">Not assigned</Tag> },
    { title: 'Status', dataIndex: 'active', render: active => active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag> },
    { title: 'Permission version', dataIndex: 'permissionVersion' },
    { title: 'Action', render: (_, bus) => <Button type="link" icon={<EditOutlined />} onClick={() => setEditing(bus)}>Edit</Button> },
  ]} /><BusModal open={editing !== undefined} bus={editing ?? null} client={client} reload={reload} onClose={() => setEditing(undefined)} /></Card>
}

function BusModal({ open, bus, client, reload, onClose }: { open: boolean; bus: Bus | null; client: Client; reload: () => Promise<void>; onClose: () => void }) {
  const [form] = Form.useForm(); const [saving, setSaving] = useState(false)
  useEffect(() => { if (open) form.setFieldsValue(bus ? { code: bus.code, name: bus.name, active: bus.active, clearPermissions: false } : { code: '', name: '', hardwareSerial: '', active: true }) }, [open, bus, form])
  const save = async (values: { code: string; name: string; hardwareSerial?: string; active: boolean; clearPermissions?: boolean }) => {
    setSaving(true)
    try {
      if (bus) await client.updateBus(bus.id, { code: values.code, name: values.name, active: values.active, clearPermissions: values.clearPermissions ?? false })
      else await client.createBus({ code: values.code, name: values.name, hardwareSerial: values.hardwareSerial!, active: values.active })
      await reload(); message.success(bus ? 'Bus updated' : 'Bus added'); onClose()
    } catch (cause) { message.error(cause instanceof Error ? cause.message : 'Unable to save bus') } finally { setSaving(false) }
  }
  const activeValue = Form.useWatch('active', form)
  const reactivating = Boolean(bus && !bus.active && activeValue)
  return <Modal open={open} title={bus ? 'Edit bus' : 'Add bus'} onCancel={onClose} onOk={() => form.submit()} confirmLoading={saving} destroyOnHidden><Form form={form} layout="vertical" onFinish={save}><Form.Item name="code" label="Bus code" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="name" label="Display name" rules={[{ required: true }]}><Input /></Form.Item>{!bus && <Form.Item name="hardwareSerial" label="Hardware serial" rules={[{ required: true }]}><Input /></Form.Item>}<Form.Item name="active" valuePropName="checked"><Checkbox>Active</Checkbox></Form.Item>{reactivating && bus && bus.permissionCount > 0 && <Alert type="warning" showIcon message={`This bus has ${bus.permissionCount} existing permissions`} description={<Form.Item name="clearPermissions" valuePropName="checked" noStyle><Checkbox>Clear existing permissions when reactivating</Checkbox></Form.Item>} />}</Form></Modal>
}

function DevicePanel({ client, buses, devices, reload }: { client: Client; buses: Bus[]; devices: Device[]; reload: () => Promise<void> }) {
  const [editing, setEditing] = useState<Device | null | undefined>(); const [historyDevice, setHistoryDevice] = useState<Device>()
  return <Card><Alert className="toolbar" type="info" showIcon message="Before moving a device, sync pending offline data, deactivate it, reassign it, then activate it on the new bus." /><Button className="toolbar" icon={<MobileOutlined />} onClick={() => setEditing(null)}>Register device</Button><Table rowKey="id" dataSource={devices} pagination={false} columns={[
    { title: 'Device code', dataIndex: 'deviceCode' }, { title: 'Hardware serial', dataIndex: 'hardwareSerial' }, { title: 'Bus', dataIndex: 'busCode' },
    { title: 'Status', dataIndex: 'active', render: active => active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag> },
    { title: 'Last seen', dataIndex: 'lastSeenAt', render: value => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : 'Never' },
    { title: 'Action', render: (_, device) => <Space><Button type="link" icon={<EditOutlined />} onClick={() => setEditing(device)}>Edit</Button><Button type="link" icon={<HistoryOutlined />} onClick={() => setHistoryDevice(device)}>History</Button></Space> },
  ]} /><DeviceModal open={editing !== undefined} device={editing ?? null} client={client} buses={buses} reload={reload} onClose={() => setEditing(undefined)} />{historyDevice && <AssignmentHistoryModal device={historyDevice} client={client} onClose={() => setHistoryDevice(undefined)} />}</Card>
}

function DeviceModal({ open, device, client, buses, reload, onClose }: { open: boolean; device: Device | null; client: Client; buses: Bus[]; reload: () => Promise<void>; onClose: () => void }) {
  const [form] = Form.useForm(); const [saving, setSaving] = useState(false)
  useEffect(() => { if (open) form.setFieldsValue(device ?? { deviceCode: '', hardwareSerial: '', busId: undefined, active: true }) }, [open, device, form])
  const save = async (values: Omit<Device, 'id' | 'busCode' | 'lastSeenAt'>) => { setSaving(true); try { if (device) await client.updateDevice(device.id, values); else await client.createDevice(values); await reload(); message.success(device ? 'Device updated' : 'Device registered'); onClose() } catch (cause) { message.error(cause instanceof Error ? cause.message : 'Unable to save device') } finally { setSaving(false) } }
  return <Modal open={open} title={device ? 'Edit device' : 'Register device'} onCancel={onClose} onOk={() => form.submit()} confirmLoading={saving} destroyOnHidden><Form form={form} layout="vertical" onFinish={save}><Form.Item name="deviceCode" label="Device code" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="hardwareSerial" label="Hardware serial" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="busId" label="Bus" rules={[{ required: true }]}><Select options={buses.filter(bus => bus.active).map(bus => ({ value: bus.id, label: `${bus.code} - ${bus.name}` }))} /></Form.Item><Form.Item name="active" valuePropName="checked"><Checkbox>Active</Checkbox></Form.Item></Form></Modal>
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
    { title: 'Employee number', dataIndex: 'employeeNo' }, { title: 'Name', dataIndex: 'name' }, { title: 'CardSN', dataIndex: 'cardSn' },
    { title: 'Status', dataIndex: 'active', render: active => active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag> },
    { title: 'Action', render: (_, employee) => <Button type="link" icon={<EditOutlined />} onClick={() => setEditing(employee)}>Edit</Button> },
  ]} /><EmployeeModal open={editing !== undefined} employee={editing ?? null} client={client} reload={reload} onClose={() => setEditing(undefined)} /></Card>
}

function EmployeeModal({ open, employee, client, reload, onClose }: { open: boolean; employee: Employee | null; client: Client; reload: () => Promise<void>; onClose: () => void }) {
  const [form] = Form.useForm(); const [saving, setSaving] = useState(false)
  useEffect(() => { if (open) form.setFieldsValue(employee ?? { employeeNo: '', name: '', cardSn: '', active: true }) }, [open, employee, form])
  const save = async (values: Omit<Employee, 'id'>) => { setSaving(true); try { if (employee) await client.updateEmployee(employee.id, values); else await client.createEmployee(values); await reload(); message.success(employee ? 'Employee updated' : 'Employee added'); onClose() } catch (cause) { message.error(cause instanceof Error ? cause.message : 'Unable to save employee') } finally { setSaving(false) } }
  return <Modal open={open} title={employee ? 'Edit employee' : 'Add employee'} onCancel={onClose} onOk={() => form.submit()} confirmLoading={saving} destroyOnHidden><Form form={form} layout="vertical" onFinish={save}><Form.Item name="employeeNo" label="Employee number" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="name" label="Name" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="cardSn" label="CardSN" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="active" valuePropName="checked"><Checkbox>Active</Checkbox></Form.Item></Form></Modal>
}

function BusSelector({ buses, value, onChange }: { buses: Bus[]; value?: string; onChange: (value: string) => void }) {
  return <Select value={value} onChange={onChange} placeholder="Select a bus" style={{ minWidth: 280 }} options={buses.map(bus => ({ value: bus.id, label: `${bus.code} - ${bus.name}${bus.active ? '' : ' (Inactive)'}` }))} />
}

function PermissionPanel({ client, buses, employees, selectedBusId, setSelectedBusId, reload }: { client: Client; buses: Bus[]; employees: Employee[]; selectedBusId?: string; setSelectedBusId: (value: string) => void; reload: () => Promise<void> }) {
  const [allowed, setAllowed] = useState<Employee[]>([]); const [addOpen, setAddOpen] = useState(false)
  const load = useCallback(async () => { setAllowed(selectedBusId ? await client.permissions(selectedBusId) : []) }, [client, selectedBusId])
  useEffect(() => {
    // Permission data is owned by the selected bus on the backend.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])
  const remove = (employee: Employee) => {
    if (!selectedBusId) return
    Modal.confirm({
      title: "Remove boarding permission?",
      content: "Remove permission for " + employee.name + " (" + employee.employeeNo + ") from this bus?",
      okText: "Remove permission",
      okButtonProps: { danger: true },
      cancelText: "Cancel",
      onOk: async () => {
        try {
          await client.revoke(selectedBusId, employee.id)
          await Promise.all([load(), reload()])
          message.success("Permission removed")
        } catch (cause) {
          message.error(cause instanceof Error ? cause.message : "Unable to remove permission")
          throw cause
        }
      },
    })
  }
  return <Card><Space className="toolbar" wrap><BusSelector buses={buses} value={selectedBusId} onChange={setSelectedBusId} /><Button icon={<IdcardOutlined />} disabled={!selectedBusId} onClick={() => setAddOpen(true)}>Add employee</Button></Space><List dataSource={allowed} locale={{ emptyText: 'No allowed employees' }} renderItem={employee => <List.Item actions={[<Button key="remove" danger type="link" onClick={() => remove(employee)}>Remove permission</Button>]}><List.Item.Meta title={<Space>{employee.name}{employee.active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag>}</Space>} description={`${employee.employeeNo} / CardSN: ${employee.cardSn}`} /></List.Item>} />{addOpen && selectedBusId && <AddPermissionModal client={client} busId={selectedBusId} employees={employees} allowed={allowed} onClose={() => setAddOpen(false)} onSaved={async () => { setAddOpen(false); await Promise.all([load(), reload()]) }} />}</Card>
}

function AddPermissionModal({ client, busId, employees, allowed, onClose, onSaved }: { client: Client; busId: string; employees: Employee[]; allowed: Employee[]; onClose: () => void; onSaved: () => Promise<void> }) {
  const [query, setQuery] = useState(''); const [selected, setSelected] = useState<string[]>([]); const [saving, setSaving] = useState(false)
  const allowedIds = new Set(allowed.map(employee => employee.id)); const candidates = employees.filter(employee => employee.active && !allowedIds.has(employee.id) && employee.employeeNo.toLowerCase().includes(query.trim().toLowerCase()))
  const save = async () => { setSaving(true); try { await client.grantBatch(busId, selected); await onSaved(); message.success(`${selected.length} employee(s) added`) } catch (cause) { message.error(cause instanceof Error ? cause.message : 'Unable to add employees') } finally { setSaving(false) } }
  return <Modal open title="Add employees from employee table" onCancel={onClose} onOk={() => void save()} okButtonProps={{ disabled: selected.length === 0 }} confirmLoading={saving} width={720}><Input.Search allowClear placeholder="Search employee number" value={query} onChange={event => setQuery(event.target.value)} className="toolbar" /><Table rowKey="id" dataSource={candidates} pagination={{ pageSize: 8 }} rowSelection={{ selectedRowKeys: selected, onChange: keys => setSelected(keys.map(String)), preserveSelectedRowKeys: true }} columns={[{ title: 'Employee number', dataIndex: 'employeeNo' }, { title: 'Name', dataIndex: 'name' }, { title: 'CardSN', dataIndex: 'cardSn' }]} /></Modal>
}

function RoutePanel({ client, buses, selectedBusId, setSelectedBusId }: { client: Client; buses: Bus[]; selectedBusId?: string; setSelectedBusId: (value: string) => void }) {
  const [date, setDate] = useState<Dayjs>(dayjs()); const [route, setRoute] = useState<RouteHistory>(); const [loading, setLoading] = useState(false)
  const search = async () => { if (!selectedBusId) return; setLoading(true); try { setRoute(await client.route(selectedBusId, date.startOf('day').toISOString(), date.endOf('day').toISOString())) } finally { setLoading(false) } }
  const positions = route?.points.map(point => [point.latitude, point.longitude] as [number, number]) ?? []; const center = positions[0] ?? [10.8231, 106.6297]
  return <Card><Space wrap className="toolbar"><BusSelector buses={buses} value={selectedBusId} onChange={setSelectedBusId} /><DatePicker value={date} onChange={value => value && setDate(value)} /><Button type="primary" loading={loading} onClick={() => void search()}>Load route</Button><Tag>{positions.length} points</Tag></Space><MapContainer key={`${center[0]}-${center[1]}-${positions.length}`} center={center} zoom={13} className="route-map"><TileLayer attribution="&copy; OpenStreetMap contributors" url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />{positions.length > 1 && <Polyline positions={positions} color="#1677ff" weight={5} />}</MapContainer></Card>
}

function EventPanel({ client, buses, selectedBusId, setSelectedBusId }: { client: Client; buses: Bus[]; selectedBusId?: string; setSelectedBusId: (value: string) => void }) {
  const [date, setDate] = useState<Dayjs>(dayjs()); const [events, setEvents] = useState<BoardingEvent[]>([])
  const search = async () => { if (selectedBusId) setEvents(await client.events(selectedBusId, date.startOf('day').toISOString(), date.endOf('day').toISOString())) }
  return <Card><Space wrap className="toolbar"><BusSelector buses={buses} value={selectedBusId} onChange={setSelectedBusId} /><DatePicker value={date} onChange={value => value && setDate(value)} /><Button type="primary" onClick={() => void search()}>Load events</Button></Space><Table rowKey="id" dataSource={events} pagination={{ pageSize: 20 }} columns={[{ title: 'Time', dataIndex: 'scannedAt', render: value => dayjs(value).format('YYYY-MM-DD HH:mm:ss') }, { title: 'CardSN', dataIndex: 'cardSn' }, { title: 'Result', dataIndex: 'result', render: value => <Tag color={value === 'ALLOWED' ? 'green' : 'red'}>{value}</Tag> }, { title: 'Permission version', dataIndex: 'permissionVersion' }]} /></Card>
}

export default App
