import { useState } from 'react'
import { Button, Card, DatePicker, Select, Space, Tag, TimePicker } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { CircleMarker, MapContainer, Polyline, TileLayer, Tooltip } from 'react-leaflet'
import { api, type Bus, type RouteHistory } from './api'

type Client = ReturnType<typeof api>

export default function RouteHistoryPanel({ client, buses, selectedBusId, setSelectedBusId }: { client: Client; buses: Bus[]; selectedBusId?: string; setSelectedBusId: (value: string) => void }) {
  const [date, setDate] = useState<Dayjs>(dayjs())
  const [timeRange, setTimeRange] = useState<[Dayjs, Dayjs]>([
    dayjs().startOf('day'),
    dayjs().endOf('day'),
  ])
  const [history, setHistory] = useState<RouteHistory>()
  const [loading, setLoading] = useState(false)
  const search = async () => {
    if (!selectedBusId) return
    setLoading(true)
    const [startTime, endTime] = timeRange
    const from = date.startOf('day').hour(startTime.hour()).minute(startTime.minute()).second(0).millisecond(0)
    const to = date.startOf('day').hour(endTime.hour()).minute(endTime.minute()).second(59).millisecond(999)
    try { setHistory(await client.route(selectedBusId, from.toISOString(), to.toISOString())) }
    finally { setLoading(false) }
  }
  const positions = history?.points.map(point => [point.latitude, point.longitude] as [number, number]) ?? []
  const center = positions[0] ?? [10.8231, 106.6297]
  return <Card>
    <Space wrap className="toolbar">
      <Select value={selectedBusId} onChange={setSelectedBusId} placeholder="Select a bus" style={{ minWidth: 280 }} options={buses.map(bus => ({ value: bus.id, label: `${bus.code} - ${bus.name}` }))} />
      <DatePicker value={date} onChange={value => value && setDate(value)} />
      <TimePicker.RangePicker
        value={timeRange}
        onChange={value => value?.[0] && value[1] && setTimeRange([value[0], value[1]])}
        format="HH:mm"
        allowClear={false}
      />
      <Button type="primary" loading={loading} onClick={() => void search()}>Load route</Button>
      <Tag>{positions.length} points</Tag>
      <Tag color="blue">Mileage: {history?.dailyMileageKm.toFixed(2) ?? '0.00'} km</Tag>
    </Space>
    <MapContainer key={`${center[0]}-${center[1]}-${positions.length}`} center={center} zoom={13} className="route-map">
      <TileLayer attribution="&copy; OpenStreetMap contributors" url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      {positions.length > 1 && <Polyline positions={positions} color="#1677ff" weight={5} />}
      {history?.points.map((point, index) => <CircleMarker key={`${point.recordedAt}-${index}`} center={[point.latitude, point.longitude]} radius={3} pathOptions={{ color: '#1677ff', opacity: 0.35, fillOpacity: 0.2 }}><Tooltip>{dayjs(point.recordedAt).format('HH:mm')}</Tooltip></CircleMarker>)}
    </MapContainer>
  </Card>
}
