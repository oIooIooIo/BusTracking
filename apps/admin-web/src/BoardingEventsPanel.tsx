import { useState } from 'react'
import { Button, Card, DatePicker, Descriptions, Select, Space, Table, Tag } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { api, type BoardingEvent, type Bus } from './api'

type Client = ReturnType<typeof api>

export default function BoardingEventsPanel({ client, buses, selectedBusId, setSelectedBusId }: { client: Client; buses: Bus[]; selectedBusId?: string; setSelectedBusId: (value: string) => void }) {
  const [date, setDate] = useState<Dayjs>(dayjs())
  const [events, setEvents] = useState<BoardingEvent[]>([])
  const search = async () => { if (selectedBusId) setEvents(await client.events(selectedBusId, date.startOf('day').toISOString(), date.endOf('day').toISOString())) }
  return <Card>
    <Space wrap className="toolbar">
      <Select value={selectedBusId} onChange={setSelectedBusId} placeholder="Select a bus" style={{ minWidth: 280 }} options={buses.map(bus => ({ value: bus.id, label: `${bus.code} - ${bus.name}` }))} />
      <DatePicker value={date} onChange={value => value && setDate(value)} />
      <Button type="primary" onClick={() => void search()}>Load events</Button>
    </Space>
    <Table rowKey="id" dataSource={events} pagination={{ pageSize: 20 }} columns={[
      { title: 'Time', dataIndex: 'scannedAt', render: value => dayjs(value).format('YYYY-MM-DD HH:mm:ss') },
      { title: 'Employee number', dataIndex: 'employeeNo', render: value => value ?? '—' },
      { title: 'Employee name', dataIndex: 'employeeName', render: value => value ?? '—' },
      { title: 'Employee Department', dataIndex: 'employeeDepartment', render: value => value ?? '—' },
      { title: 'Result', dataIndex: 'result', render: value => <Tag color={value === 'ALLOWED' ? 'green' : 'red'}>{value}</Tag> },
      { title: 'Route', dataIndex: 'routes', render: routes => routes.length ? routes.map((route: { code: string }) => route.code).join(', ') : '—' },
      { title: 'Stop', dataIndex: 'stopName', render: value => value ?? 'Unknown' },
    ]} expandable={{ expandedRowRender: event => <Descriptions size="small" column={2} items={[
      { key: 'bus', label: 'Bus ID', children: event.busId },
      { key: 'device', label: 'Device ID', children: event.deviceId },
      { key: 'card', label: 'Card SN', children: event.cardSn.length > 4 ? `••••${event.cardSn.slice(-4)}` : '••••' },
      { key: 'type', label: 'Event type', children: event.eventType },
      { key: 'location', label: 'Location', children: event.latitude == null ? 'Unavailable' : `${event.latitude}, ${event.longitude}` },
      { key: 'source', label: 'Location source', children: event.locationSource },
      { key: 'recorded', label: 'Location time', children: event.locationRecordedAt ? dayjs(event.locationRecordedAt).format('YYYY-MM-DD HH:mm:ss') : '—' },
      { key: 'accuracy', label: 'Accuracy', children: event.accuracyMeters == null ? '—' : `${event.accuracyMeters} m` },
      { key: 'version', label: 'Permission version', children: event.permissionVersion ?? '—' },
      { key: 'id', label: 'Event ID', children: event.id },
    ]} /> }} />
  </Card>
}
