import { useEffect, useMemo, useState } from 'react'
import { ArrowDownOutlined, ArrowLeftOutlined, ArrowUpOutlined, EditOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons'
import { Alert, Breadcrumb, Button, Card, Checkbox, Descriptions, Form, Input, InputNumber, Modal, Space, Table, Tag, Typography, Upload, message } from 'antd'
import L from 'leaflet'
import { Circle, MapContainer, Marker, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import { api, type Employee, type Route, type RouteImportResult, type RouteSummary, type Stop, type StopInput } from './api'

type Client = ReturnType<typeof api>
type Point = { latitude: number; longitude: number }
const HANOI: Point = { latitude: 21.0278, longitude: 105.8342 }
const markerIcon = L.divIcon({
  className: '',
  html: '<div style="width:18px;height:18px;border-radius:50%;background:#1677ff;border:3px solid white;box-shadow:0 1px 5px #333"></div>',
  iconSize: [18, 18], iconAnchor: [9, 9],
})

export default function RouteManagementPanel({ client, routes, employees, reload }: { client: Client; routes: Route[]; employees: Employee[]; reload: () => Promise<void> }) {
  const [showStops, setShowStops] = useState(false)
  const [selectedRouteId, setSelectedRouteId] = useState<string>()
  const [editing, setEditing] = useState<Route | null | undefined>()
  const [allowed, setAllowed] = useState<Employee[]>([])
  const [adding, setAdding] = useState(false)
  const [importing, setImporting] = useState(false)
  const activeRouteId = selectedRouteId ?? routes[0]?.id
  const selectedRoute = routes.find(route => route.id === activeRouteId)

  useEffect(() => { if (activeRouteId) void client.routePermissions(activeRouteId).then(setAllowed) }, [client, activeRouteId])
  const refreshPermissions = async () => { if (activeRouteId) setAllowed(await client.routePermissions(activeRouteId)); await reload() }
  const revoke = async (employeeId: string) => { if (!activeRouteId) return; await client.revokeRoutePermission(activeRouteId, employeeId); await refreshPermissions() }

  if (showStops) return <StopCatalog client={client} onBack={() => setShowStops(false)} onChanged={reload} />
  return <Space direction="vertical" size="large" style={{ width: '100%' }}>
    <Card title="Routes" extra={<Space><Button icon={<UploadOutlined />} onClick={() => setImporting(true)}>Import Excel</Button><Button onClick={() => setShowStops(true)}>Manage stops</Button><Button icon={<PlusOutlined />} onClick={() => setEditing(null)}>Add route</Button></Space>}>
      <Table rowKey="id" dataSource={routes} pagination={false} onRow={route => ({ onClick: () => setSelectedRouteId(route.id) })} rowClassName={route => route.id === activeRouteId ? 'selected-row' : ''} columns={[
        { title: 'Route code', dataIndex: 'code' }, { title: 'Name', dataIndex: 'name' },
        { title: 'Stops', render: (_, route: Route) => route.stops.map(stop => stop.code).join(' → ') || '—' },
        { title: 'Employees', dataIndex: 'employeeCount' }, { title: 'Buses', dataIndex: 'busCount' },
        { title: 'Status', dataIndex: 'active', render: active => active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag> },
        { title: 'Action', render: (_, route: Route) => <Button type="link" icon={<EditOutlined />} onClick={event => { event.stopPropagation(); setEditing(route) }}>Edit</Button> },
      ]} />
    </Card>
    <Card title={selectedRoute ? `Allowed employees — ${selectedRoute.code}` : 'Allowed employees'} extra={<Button disabled={!selectedRoute} onClick={() => setAdding(true)}>Add employees</Button>}>
      <Table rowKey="id" dataSource={allowed} pagination={{ pageSize: 10 }} columns={[
        { title: 'Employee number', dataIndex: 'employeeNo' }, { title: 'Name', dataIndex: 'name' }, { title: 'Department', dataIndex: 'department' },
        { title: 'Action', render: (_, employee: Employee) => <Button danger type="link" onClick={() => void revoke(employee.id)}>Remove</Button> },
      ]} />
    </Card>
    {editing !== undefined && <RouteModal key={editing?.id ?? "new"} route={editing} client={client} onClose={() => setEditing(undefined)} reload={reload} />}
    {adding && activeRouteId && <AddEmployeesModal routeId={activeRouteId} client={client} employees={employees} allowed={allowed} onClose={() => setAdding(false)} onSaved={async () => { setAdding(false); await refreshPermissions() }} />}
    {importing && <RouteImportModal client={client} onClose={() => setImporting(false)} onImported={reload} />}
  </Space>
}

function RouteImportModal({ client, onClose, onImported }: { client: Client; onClose: () => void; onImported: () => Promise<void> }) {
  const [file, setFile] = useState<File>()
  const [result, setResult] = useState<RouteImportResult>()
  const [loading, setLoading] = useState(false)
  const preview = async () => {
    if (!file) return
    setLoading(true)
    try { setResult(await client.previewRouteImport(file)) }
    catch (cause) { message.error(cause instanceof Error ? cause.message : 'Unable to preview Excel import') }
    finally { setLoading(false) }
  }
  const commit = async () => {
    if (!file) return
    setLoading(true)
    try {
      setResult(await client.importRoutes(file))
      await onImported()
      message.success('Valid routes and permissions imported')
    } catch (cause) { message.error(cause instanceof Error ? cause.message : 'Unable to import Excel file') }
    finally { setLoading(false) }
  }
  const canCommit = Boolean(result && !result.committed && (result.newRouteCount > 0 || result.newPermissionCount > 0))
  return <Modal
    open
    width={1000}
    title="Import route permissions from Excel"
    onCancel={onClose}
    footer={<Space>
      <Button onClick={onClose}>{result?.committed ? 'Close' : 'Cancel'}</Button>
      {!result && <Button type="primary" loading={loading} disabled={!file} onClick={() => void preview()}>Preview</Button>}
      {result && !result.committed && <Button onClick={() => { setResult(undefined); setFile(undefined) }}>Choose another file</Button>}
      {canCommit && <Button type="primary" loading={loading} onClick={() => void commit()}>Import valid rows</Button>}
    </Space>}
    destroyOnHidden
  >
    <Alert
      type="info"
      showIcon
      message="This import only adds data"
      description="Số tuyến maps to Route Code. Existing routes and employees are not overwritten, existing permissions are kept, and employees missing from the system are skipped because CardSN is required."
      style={{ marginBottom: 16 }}
    />
    {!result && <Upload.Dragger
      accept=".xlsx"
      maxCount={1}
      beforeUpload={selected => { setFile(selected); return false }}
      onRemove={() => { setFile(undefined); return true }}
    >
      <p className="ant-upload-drag-icon"><UploadOutlined /></p>
      <p>Choose the GA .xlsx file</p>
      <p>Required sheet: Master Data. Required columns: ID, Name, Function, Số tuyến, Bus Route.</p>
    </Upload.Dragger>}
    {result && <RouteImportReport result={result} />}
  </Modal>
}

function RouteImportReport({ result }: { result: RouteImportResult }) {
  return <Space direction="vertical" size="middle" style={{ width: '100%' }}>
    <Alert
      type={result.committed ? 'success' : result.errorCount ? 'warning' : 'info'}
      showIcon
      message={result.committed ? 'Import completed' : 'Preview completed — no data has been changed'}
      description={result.errorCount ? `${result.errorCount} error(s) will be skipped; valid rows can still be imported.` : 'No blocking row errors were found.'}
    />
    <Descriptions bordered size="small" column={4} items={[
      { key: 'rows', label: 'Excel rows', children: result.totalRows },
      { key: 'valid', label: 'Valid permissions', children: result.validPermissionRows },
      { key: 'skipped', label: 'Skipped rows', children: result.skippedRows },
      { key: 'errors', label: 'Errors / warnings', children: `${result.errorCount} / ${result.warningCount}` },
      { key: 'newRoutes', label: result.committed ? 'Routes created' : 'Routes to create', children: result.newRouteCount },
      { key: 'existingRoutes', label: 'Existing routes', children: result.existingRouteCount },
      { key: 'newPermissions', label: result.committed ? 'Permissions created' : 'Permissions to create', children: result.newPermissionCount },
      { key: 'existingPermissions', label: 'Existing permissions', children: result.existingPermissionCount },
    ]} />
    <Table
      rowKey={(_, index) => String(index)}
      size="small"
      dataSource={result.issues}
      pagination={{ pageSize: 10 }}
      locale={{ emptyText: 'No errors or warnings' }}
      columns={[
        { title: 'Row', dataIndex: 'rowNumber', width: 70, render: value => value || '—' },
        { title: 'Level', dataIndex: 'severity', width: 90, render: value => value === 'ERROR' ? <Tag color="red">Error</Tag> : <Tag color="orange">Warning</Tag> },
        { title: 'Route', dataIndex: 'routeCode', width: 90, render: value => value ?? '—' },
        { title: 'Employee ID', dataIndex: 'employeeNo', width: 130, render: value => value ?? '—' },
        { title: 'Details', dataIndex: 'message' },
      ]}
    />
  </Space>
}

function RouteModal({ route, client, onClose, reload }: { route: Route | null; client: Client; onClose: () => void; reload: () => Promise<void> }) {
  const [form] = Form.useForm()
  const [stops, setStops] = useState<Stop[]>(route?.stops ?? [])
  const [picker, setPicker] = useState(false)
  const [saving, setSaving] = useState(false)
  const move = (index: number, delta: number) => setStops(current => { const next = [...current]; const target = index + delta; if (target < 0 || target >= next.length) return current; [next[index], next[target]] = [next[target], next[index]]; return next })
  const save = async (values: { code: string; name: string; active: boolean }) => {
    setSaving(true)
    try {
      const input = { ...values, stopIds: stops.map(stop => stop.id) }
      if (route) await client.updateRoute(route.id, input); else await client.createRoute(input)
      await reload(); message.success(route ? 'Route updated' : 'Route added'); onClose()
    } catch (cause) { message.error(cause instanceof Error ? cause.message : 'Unable to save route') } finally { setSaving(false) }
  }
  const center = stops[0] ?? HANOI
  return <>
    <Modal width={850} open title={route ? 'Edit route' : 'Add route'} onCancel={onClose} onOk={() => form.submit()} confirmLoading={saving} destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={route ?? { code: "", name: "", active: true }} onFinish={save}>
        <Space align="start"><Form.Item name="code" label="Route code" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="name" label="Route name" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="active" valuePropName="checked"><Checkbox>Active</Checkbox></Form.Item></Space>
        <Typography.Title level={5}>Stops</Typography.Title>
        <Table rowKey="id" size="small" pagination={false} dataSource={stops} columns={[
          { title: '#', render: (_, __, index) => index + 1 }, { title: 'Stop code', dataIndex: 'code' }, { title: 'Name', dataIndex: 'name' },
          { title: 'Status', render: (_, stop: Stop) => stop.active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag> },
          { title: 'Order', render: (_, __, index) => <Space><Button size="small" icon={<ArrowUpOutlined />} disabled={!index} onClick={() => move(index, -1)} /><Button size="small" icon={<ArrowDownOutlined />} disabled={index === stops.length - 1} onClick={() => move(index, 1)} /></Space> },
          { title: 'Action', render: (_, stop: Stop) => <Button danger type="link" onClick={() => setStops(current => current.filter(item => item.id !== stop.id))}>Remove</Button> },
        ]} />
        <Button style={{ marginTop: 12 }} type="dashed" icon={<PlusOutlined />} onClick={() => setPicker(true)}>Add stop</Button>
      </Form>
    </Modal>
    <StopPicker open={picker} client={client} excluded={new Set(stops.map(stop => stop.id))} initialCenter={center} onClose={() => setPicker(false)} onSelect={stop => { setStops(current => [...current, stop]); setPicker(false) }} />
  </>
}

function StopPicker({ open, client, excluded, initialCenter, onClose, onSelect }: { open: boolean; client: Client; excluded: Set<string>; initialCenter: Point; onClose: () => void; onSelect: (stop: Stop) => void }) {
  const [query, setQuery] = useState('')
  const [rows, setRows] = useState<Stop[]>([])
  const [creating, setCreating] = useState(false)
  useEffect(() => { if (open) void client.stops(query, true).then(setRows).catch(cause => message.error(String(cause))) }, [open, query, client])
  const candidates = rows.filter(stop => !excluded.has(stop.id))
  return <>
    <Modal open={open} title="Add stop to route" footer={null} onCancel={onClose} width={760} destroyOnHidden>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Input.Search allowClear placeholder="Search stop code or name" value={query} onChange={event => setQuery(event.target.value)} />
        <Table rowKey="id" size="small" dataSource={candidates} pagination={{ pageSize: 6 }} locale={{ emptyText: 'No matching active stops' }} columns={[
          { title: 'Stop code', dataIndex: 'code' }, { title: 'Name', dataIndex: 'name' },
          { title: 'Location', render: (_, stop: Stop) => `${stop.latitude.toFixed(6)}, ${stop.longitude.toFixed(6)}` },
          { title: 'Action', render: (_, stop: Stop) => <Button type="link" onClick={() => onSelect(stop)}>Add</Button> },
        ]} />
        <Button icon={<PlusOutlined />} onClick={() => setCreating(true)}>Create new stop</Button>
      </Space>
    </Modal>
    <StopEditor open={creating} client={client} stop={null} initialCenter={initialCenter} onClose={() => setCreating(false)} onSaved={stop => { setCreating(false); onSelect(stop) }} />
  </>
}

function StopCatalog({ client, onBack, onChanged }: { client: Client; onBack: () => void; onChanged: () => Promise<void> }) {
  const [query, setQuery] = useState('')
  const [rows, setRows] = useState<Stop[]>([])
  const [editing, setEditing] = useState<Stop | null | undefined>()
  const load = async () => setRows(await client.stops(query))
  useEffect(() => {
    let cancelled = false
    void client.stops(query).then(items => { if (!cancelled) setRows(items) }).catch(cause => message.error(String(cause)))
    return () => { cancelled = true }
  }, [client, query])
  return <Space direction="vertical" size="large" style={{ width: '100%' }}>
    <Breadcrumb items={[{ title: <a onClick={onBack}>Routes</a> }, { title: 'Stops' }]} />
    <Card title="Stops" extra={<Space><Button icon={<ArrowLeftOutlined />} onClick={onBack}>Back to routes</Button><Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing(null)}>Add stop</Button></Space>}>
      <Input.Search allowClear placeholder="Search stop code or name" value={query} onChange={event => setQuery(event.target.value)} style={{ maxWidth: 360, marginBottom: 16 }} />
      <Table rowKey="id" dataSource={rows} columns={[
        { title: 'Stop code', dataIndex: 'code' }, { title: 'Name', dataIndex: 'name' },
        { title: 'Location', render: (_, stop: Stop) => `${stop.latitude.toFixed(6)}, ${stop.longitude.toFixed(6)}` },
        { title: 'Radius', render: (_, stop: Stop) => `${stop.radiusMeters} m` }, { title: 'Routes', dataIndex: 'routeCount' },
        { title: 'Status', render: (_, stop: Stop) => stop.active ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag> },
        { title: 'Action', render: (_, stop: Stop) => <Button type="link" icon={<EditOutlined />} onClick={() => setEditing(stop)}>Edit</Button> },
      ]} />
    </Card>
    <StopEditor open={editing !== undefined} client={client} stop={editing ?? null} initialCenter={editing ?? HANOI} onClose={() => setEditing(undefined)} onSaved={async () => { setEditing(undefined); await load(); await onChanged() }} />
  </Space>
}

function StopEditor({ open, client, stop, initialCenter, onClose, onSaved }: { open: boolean; client: Client; stop: Stop | null; initialCenter: Point; onClose: () => void; onSaved: (stop: Stop) => void | Promise<void> }) {
  const [form] = Form.useForm<StopInput>()
  const [saving, setSaving] = useState(false)
  const [usedBy, setUsedBy] = useState<RouteSummary[]>([])
  const latitude = Form.useWatch('latitude', form) ?? initialCenter.latitude
  const longitude = Form.useWatch('longitude', form) ?? initialCenter.longitude
  const radiusMeters = Form.useWatch('radiusMeters', form) ?? 100
  useEffect(() => {
    if (!open) return
    form.setFieldsValue(stop ? { name: stop.name, latitude: stop.latitude, longitude: stop.longitude, radiusMeters: stop.radiusMeters, active: stop.active } : { name: '', latitude: initialCenter.latitude, longitude: initialCenter.longitude, radiusMeters: 100, active: true })
    void (stop?.routeCount ? client.stopRoutes(stop.id) : Promise.resolve([])).then(setUsedBy)
  }, [open, stop, initialCenter.latitude, initialCenter.longitude, form, client])
  const save = async (values: StopInput) => {
    setSaving(true)
    try {
      const saved = stop ? await client.updateStop(stop.id, values) : await client.createStop(values)
      message.success(stop ? 'Stop updated' : `Stop ${saved.code} created`); await onSaved(saved)
    } catch (cause) { message.error(cause instanceof Error ? cause.message : 'Unable to save stop') } finally { setSaving(false) }
  }
  return <Modal width={900} open={open} title={stop ? `Edit stop — ${stop.code}` : 'Add stop'} onCancel={onClose} onOk={() => form.submit()} confirmLoading={saving} destroyOnHidden>
    {usedBy.length > 0 && <Alert style={{ marginBottom: 16 }} type="warning" showIcon message="This is a shared stop" description={`Changes affect: ${usedBy.map(route => `${route.code} ${route.name}`).join(', ')}`} />}
    <Form form={form} layout="vertical" onFinish={save}>
      {stop && <Form.Item label="Stop code"><Input value={stop.code} readOnly /></Form.Item>}
      <Form.Item name="name" label="Stop name" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
      <Space align="start" wrap>
        <Form.Item name="latitude" label="Latitude" rules={[{ required: true }]}><InputNumber min={-90} max={90} precision={6} style={{ width: 180 }} /></Form.Item>
        <Form.Item name="longitude" label="Longitude" rules={[{ required: true }]}><InputNumber min={-180} max={180} precision={6} style={{ width: 180 }} /></Form.Item>
        <Form.Item name="radiusMeters" label="Radius (m)" rules={[{ required: true }]}><InputNumber min={1} style={{ width: 140 }} /></Form.Item>
        <Form.Item name="active" valuePropName="checked"><Checkbox>Active</Checkbox></Form.Item>
      </Space>
      <Typography.Text type="secondary">Click the map or drag the marker to set the coordinates.</Typography.Text>
      <StopMap latitude={latitude} longitude={longitude} radiusMeters={radiusMeters} onChange={(lat, lng) => form.setFieldsValue({ latitude: lat, longitude: lng })} />
    </Form>
  </Modal>
}

function StopMap({ latitude, longitude, radiusMeters, onChange }: { latitude: number; longitude: number; radiusMeters: number; onChange: (latitude: number, longitude: number) => void }) {
  const position = useMemo<[number, number]>(() => [latitude, longitude], [latitude, longitude])
  return <MapContainer center={position} zoom={13} style={{ height: 360, width: '100%', marginTop: 10 }}>
    <TileLayer attribution='&copy; OpenStreetMap contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
    <MapInteraction position={position} radiusMeters={radiusMeters} onChange={onChange} />
  </MapContainer>
}

function MapInteraction({ position, radiusMeters, onChange }: { position: [number, number]; radiusMeters: number; onChange: (latitude: number, longitude: number) => void }) {
  const map = useMap()
  useEffect(() => { map.setView(position, map.getZoom()) }, [map, position])
  useMapEvents({ click: event => onChange(event.latlng.lat, event.latlng.lng) })
  return <><Circle center={position} radius={radiusMeters} pathOptions={{ color: '#1677ff', fillOpacity: 0.12 }} /><Marker position={position} icon={markerIcon} draggable eventHandlers={{ dragend: event => { const point = event.target.getLatLng(); onChange(point.lat, point.lng) } }} /></>
}

function AddEmployeesModal({ routeId, client, employees, allowed, onClose, onSaved }: { routeId: string; client: Client; employees: Employee[]; allowed: Employee[]; onClose: () => void; onSaved: () => Promise<void> }) {
  const [selected, setSelected] = useState<string[]>([])
  const [saving, setSaving] = useState(false)
  const allowedIds = new Set(allowed.map(employee => employee.id))
  const candidates = employees.filter(employee => employee.active && !allowedIds.has(employee.id))
  const save = async () => { setSaving(true); try { await client.grantRoutePermissions(routeId, selected); await onSaved(); message.success('Employees added') } finally { setSaving(false) } }
  return <Modal open title="Add employees" onCancel={onClose} onOk={() => void save()} confirmLoading={saving} okButtonProps={{ disabled: !selected.length }} width={760}>
    <Table rowKey="id" dataSource={candidates} pagination={{ pageSize: 8 }} rowSelection={{ selectedRowKeys: selected, onChange: keys => setSelected(keys.map(String)) }} columns={[
      { title: 'Employee number', dataIndex: 'employeeNo' }, { title: 'Name', dataIndex: 'name' }, { title: 'Department', dataIndex: 'department' },
    ]} />
  </Modal>
}
