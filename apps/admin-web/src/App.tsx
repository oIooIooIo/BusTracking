import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  DatePicker,
  Form,
  Input,
  Layout,
  List,
  message,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import {
  EnvironmentOutlined,
  IdcardOutlined,
  LogoutOutlined,
  MobileOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import dayjs, { Dayjs } from 'dayjs'
import { MapContainer, Polyline, TileLayer } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import './App.css'
import {
  api,
  type BoardingEvent,
  type Bus,
  type Device,
  type Employee,
  type RouteHistory,
} from './api'

const { Header, Content } = Layout

type Credentials = { username: string; password: string }

function App() {
  const [credentials, setCredentials] = useState<Credentials | null>(null)
  if (!credentials) {
    return <Login onLogin={setCredentials} />
  }
  return <AdminConsole credentials={credentials} onLogout={() => setCredentials(null)} />
}

function Login({ onLogin }: { onLogin: (credentials: Credentials) => void }) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const submit = async (values: Credentials) => {
    setLoading(true)
    setError('')
    try {
      await api(values).buses()
      onLogin(values)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <Card className="login-card">
        <Typography.Title level={2}>Bus Tracking Admin</Typography.Title>
        <Typography.Paragraph type="secondary">
          Demo credentials: admin / admin123
        </Typography.Paragraph>
        {error && <Alert type="error" message={error} showIcon />}
        <Form layout="vertical" onFinish={submit} initialValues={{ username: 'admin', password: 'admin123' }}>
          <Form.Item name="username" label="Username" rules={[{ required: true }]}>
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="Password" rules={[{ required: true }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block>
            Sign in
          </Button>
        </Form>
      </Card>
    </div>
  )
}

function AdminConsole({
  credentials,
  onLogout,
}: {
  credentials: Credentials
  onLogout: () => void
}) {
  const client = useMemo(() => api(credentials), [credentials])
  const [buses, setBuses] = useState<Bus[]>([])
  const [devices, setDevices] = useState<Device[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [selectedBusId, setSelectedBusId] = useState<string>()
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    try {
      const [busData, deviceData, employeeData] = await Promise.all([
        client.buses(),
        client.devices(),
        client.employees(),
      ])
      setBuses(busData)
      setDevices(deviceData)
      setEmployees(employeeData)
      setSelectedBusId((current) => current ?? busData[0]?.id)
      setError('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Failed to load data')
    }
  }, [client])

  useEffect(() => {
    // Remote data must be loaded when the authenticated API client changes.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void reload()
  }, [reload])

  return (
    <Layout className="app-shell">
      <Header className="app-header">
        <Space>
          <EnvironmentOutlined className="brand-icon" />
          <Typography.Title level={3}>Bus Tracking Demo</Typography.Title>
        </Space>
        <Button icon={<LogoutOutlined />} onClick={onLogout}>Sign out</Button>
      </Header>
      <Content className="app-content">
        {error && <Alert type="error" message={error} showIcon closable />}
        <Row gutter={16} className="summary-row">
          <Col xs={24} md={6}><Card><Statistic title="Buses" value={buses.length} /></Card></Col>
          <Col xs={24} md={6}><Card><Statistic title="Devices" value={devices.length} /></Card></Col>
          <Col xs={24} md={6}><Card><Statistic title="Employees" value={employees.length} /></Card></Col>
          <Col xs={24} md={6}>
            <Card><Statistic title="Selected permission version" value={buses.find((bus) => bus.id === selectedBusId)?.permissionVersion ?? 0} /></Card>
          </Col>
        </Row>
        <Tabs
          items={[
            {
              key: 'buses',
              label: 'Buses',
              children: <BusPanel client={client} buses={buses} reload={reload} />,
            },
            {
              key: 'permissions',
              label: 'Boarding permissions',
              children: (
                <PermissionPanel
                  client={client}
                  buses={buses}
                  employees={employees}
                  selectedBusId={selectedBusId}
                  setSelectedBusId={setSelectedBusId}
                  reload={reload}
                />
              ),
            },
            {
              key: 'devices',
              label: 'Devices',
              children: <DevicePanel client={client} buses={buses} devices={devices} reload={reload} />,
            },
            {
              key: 'route',
              label: 'Route history',
              children: <RoutePanel client={client} buses={buses} selectedBusId={selectedBusId} setSelectedBusId={setSelectedBusId} />,
            },
            {
              key: 'events',
              label: 'Boarding events',
              children: <EventPanel client={client} buses={buses} selectedBusId={selectedBusId} setSelectedBusId={setSelectedBusId} />,
            },
          ]}
        />
      </Content>
    </Layout>
  )
}

function BusPanel({
  client,
  buses,
  reload,
}: {
  client: Client
  buses: Bus[]
  reload: () => Promise<void>
}) {
  const [open, setOpen] = useState(false)

  return (
    <Card>
      <Space className="toolbar">
        <Button icon={<PlusOutlined />} onClick={() => setOpen(true)}>Add bus</Button>
      </Space>
      <Table
        rowKey="id"
        dataSource={buses}
        pagination={false}
        columns={[
          { title: 'Bus code', dataIndex: 'code' },
          { title: 'Display name', dataIndex: 'name' },
          {
            title: 'Hardware serial',
            dataIndex: 'hardwareSerial',
            render: (value?: string) => value ?? <Tag color="orange">Not assigned</Tag>,
          },
          {
            title: 'Status',
            dataIndex: 'active',
            render: (active: boolean) => active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag>,
          },
          { title: 'Permission version', dataIndex: 'permissionVersion' },
        ]}
      />
      <CreateBusModal open={open} onClose={() => setOpen(false)} client={client} reload={reload} />
    </Card>
  )
}

function DevicePanel({
  client,
  buses,
  devices,
  reload,
}: {
  client: Client
  buses: Bus[]
  devices: Device[]
  reload: () => Promise<void>
}) {
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()

  const save = async (values: Omit<Device, 'id' | 'busCode' | 'lastSeenAt'>) => {
    await client.createDevice(values)
    form.resetFields()
    setOpen(false)
    await reload()
    message.success('Device registered')
  }

  return (
    <Card>
      <Space className="toolbar">
        <Button icon={<MobileOutlined />} onClick={() => setOpen(true)}>Register device</Button>
      </Space>
      <Table
        rowKey="id"
        dataSource={devices}
        pagination={false}
        columns={[
          { title: 'Device code', dataIndex: 'deviceCode' },
          { title: 'Hardware serial', dataIndex: 'hardwareSerial' },
          { title: 'Bus', dataIndex: 'busCode' },
          {
            title: 'Status',
            dataIndex: 'active',
            render: (active: boolean) => active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag>,
          },
          {
            title: 'Last seen',
            dataIndex: 'lastSeenAt',
            render: (value?: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : 'Never',
          },
        ]}
      />
      <Modal open={open} title="Register device" onCancel={() => setOpen(false)} onOk={() => form.submit()} destroyOnHidden>
        <Form form={form} layout="vertical" onFinish={save} initialValues={{ active: true }}>
          <Form.Item name="deviceCode" label="Device code" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item
            name="hardwareSerial"
            label="Hardware serial"
            rules={[{ required: true }]}
            extra="Example: QCM2290-CF8F718B"
          >
            <Input />
          </Form.Item>
          <Form.Item name="busId" label="Bus" rules={[{ required: true }]}>
            <Select options={buses.map((bus) => ({ value: bus.id, label: `${bus.code} - ${bus.name}` }))} />
          </Form.Item>
          <Form.Item name="active" valuePropName="checked"><Checkbox>Active</Checkbox></Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}

type Client = ReturnType<typeof api>

function BusSelector({
  buses,
  value,
  onChange,
}: {
  buses: Bus[]
  value?: string
  onChange: (value: string) => void
}) {
  return (
    <Select
      value={value}
      onChange={onChange}
      placeholder="Select a bus"
      style={{ minWidth: 240 }}
      options={buses.map((bus) => ({
        value: bus.id,
        label: `${bus.code} - ${bus.name}`,
      }))}
    />
  )
}

function PermissionPanel({
  client,
  buses,
  employees,
  selectedBusId,
  setSelectedBusId,
  reload,
}: {
  client: Client
  buses: Bus[]
  employees: Employee[]
  selectedBusId?: string
  setSelectedBusId: (value: string) => void
  reload: () => Promise<void>
}) {
  const [allowed, setAllowed] = useState<Employee[]>([])
  const [employeeModal, setEmployeeModal] = useState(false)

  const loadPermissions = useCallback(async () => {
    if (selectedBusId) {
      setAllowed(await client.permissions(selectedBusId))
    }
  }, [client, selectedBusId])

  useEffect(() => {
    // Permission state is owned by the selected bus on the backend.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadPermissions()
  }, [loadPermissions])

  const allowedIds = new Set(allowed.map((employee) => employee.id))
  const toggle = async (employee: Employee, checked: boolean) => {
    if (!selectedBusId) return
    if (checked) await client.grant(selectedBusId, employee.id)
    else await client.revoke(selectedBusId, employee.id)
    await Promise.all([loadPermissions(), reload()])
    message.success('Permission updated')
  }

  return (
    <Card>
      <Space wrap className="toolbar">
        <BusSelector buses={buses} value={selectedBusId} onChange={setSelectedBusId} />
        <Button icon={<IdcardOutlined />} onClick={() => setEmployeeModal(true)}>Add employee</Button>
      </Space>
      <List
        dataSource={employees}
        locale={{ emptyText: 'No employees' }}
        renderItem={(employee) => (
          <List.Item
            actions={[
              <Checkbox
                key="allow"
                checked={allowedIds.has(employee.id)}
                disabled={!selectedBusId || !employee.active}
                onChange={(event) => void toggle(employee, event.target.checked)}
              >
                Allowed
              </Checkbox>,
            ]}
          >
            <List.Item.Meta
              title={<Space>{employee.name}{employee.active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag>}</Space>}
              description={`${employee.employeeNo} / CardSN: ${employee.cardSn}`}
            />
          </List.Item>
        )}
      />
      <CreateEmployeeModal open={employeeModal} onClose={() => setEmployeeModal(false)} client={client} reload={reload} />
    </Card>
  )
}

function CreateEmployeeModal({ open, onClose, client, reload }: { open: boolean; onClose: () => void; client: Client; reload: () => Promise<void> }) {
  const [form] = Form.useForm()
  const save = async (values: Omit<Employee, 'id'>) => {
    await client.createEmployee(values)
    form.resetFields()
    onClose()
    await reload()
  }
  return (
    <Modal open={open} title="Add employee" onCancel={onClose} onOk={() => form.submit()} destroyOnHidden>
      <Form form={form} layout="vertical" onFinish={save} initialValues={{ active: true }}>
        <Form.Item name="employeeNo" label="Employee number" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="name" label="Name" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="cardSn" label="CardSN" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="active" valuePropName="checked"><Checkbox>Active</Checkbox></Form.Item>
      </Form>
    </Modal>
  )
}

function CreateBusModal({ open, onClose, client, reload }: { open: boolean; onClose: () => void; client: Client; reload: () => Promise<void> }) {
  const [form] = Form.useForm()
  const save = async (
    values: Omit<Bus, 'id' | 'permissionVersion' | 'hardwareSerial'> & { hardwareSerial: string },
  ) => {
    await client.createBus(values)
    form.resetFields()
    onClose()
    await reload()
  }
  return (
    <Modal open={open} title="Add bus" onCancel={onClose} onOk={() => form.submit()} destroyOnHidden>
      <Form form={form} layout="vertical" onFinish={save} initialValues={{ active: true }}>
        <Form.Item name="code" label="Bus code" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="name" label="Display name" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item
          name="hardwareSerial"
          label="Hardware serial"
          rules={[{ required: true }]}
          extra="Shown on the bus device, for example QCM2290-CF8F718B"
        >
          <Input />
        </Form.Item>
        <Form.Item name="active" valuePropName="checked"><Checkbox>Active</Checkbox></Form.Item>
      </Form>
    </Modal>
  )
}

function RoutePanel({ client, buses, selectedBusId, setSelectedBusId }: { client: Client; buses: Bus[]; selectedBusId?: string; setSelectedBusId: (value: string) => void }) {
  const [date, setDate] = useState<Dayjs>(dayjs())
  const [route, setRoute] = useState<RouteHistory>()
  const [loading, setLoading] = useState(false)

  const search = async () => {
    if (!selectedBusId) return
    setLoading(true)
    try {
      setRoute(await client.route(selectedBusId, date.startOf('day').toISOString(), date.endOf('day').toISOString()))
    } finally {
      setLoading(false)
    }
  }

  const positions = route?.points.map((point) => [point.latitude, point.longitude] as [number, number]) ?? []
  const center = positions[0] ?? [10.8231, 106.6297]

  return (
    <Card>
      <Space wrap className="toolbar">
        <BusSelector buses={buses} value={selectedBusId} onChange={setSelectedBusId} />
        <DatePicker value={date} onChange={(value) => value && setDate(value)} />
        <Button type="primary" loading={loading} onClick={() => void search()}>Load route</Button>
        <Tag>{positions.length} points</Tag>
      </Space>
      <MapContainer key={`${center[0]}-${center[1]}-${positions.length}`} center={center} zoom={13} className="route-map">
        <TileLayer attribution="&copy; OpenStreetMap contributors" url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        {positions.length > 1 && <Polyline positions={positions} color="#1677ff" weight={5} />}
      </MapContainer>
    </Card>
  )
}

function EventPanel({ client, buses, selectedBusId, setSelectedBusId }: { client: Client; buses: Bus[]; selectedBusId?: string; setSelectedBusId: (value: string) => void }) {
  const [date, setDate] = useState<Dayjs>(dayjs())
  const [events, setEvents] = useState<BoardingEvent[]>([])
  const search = async () => {
    if (!selectedBusId) return
    setEvents(await client.events(selectedBusId, date.startOf('day').toISOString(), date.endOf('day').toISOString()))
  }
  return (
    <Card>
      <Space wrap className="toolbar">
        <BusSelector buses={buses} value={selectedBusId} onChange={setSelectedBusId} />
        <DatePicker value={date} onChange={(value) => value && setDate(value)} />
        <Button type="primary" onClick={() => void search()}>Load events</Button>
      </Space>
      <Table
        rowKey="id"
        dataSource={events}
        pagination={{ pageSize: 20 }}
        columns={[
          { title: 'Time', dataIndex: 'scannedAt', render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm:ss') },
          { title: 'CardSN', dataIndex: 'cardSn' },
          { title: 'Result', dataIndex: 'result', render: (value: string) => <Tag color={value === 'ALLOWED' ? 'green' : 'red'}>{value}</Tag> },
          { title: 'Permission version', dataIndex: 'permissionVersion' },
        ]}
      />
    </Card>
  )
}

export default App
