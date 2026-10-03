import './settings.css'

const lots = [
  ['Bãi A', '500', '600', 'Cổng chính', 'Nhà hành chính'],
  ['Bãi B', '350', '420', 'Cổng phía Đông', 'Thư viện'],
  ['Bãi C', '400', '480', 'Cổng phía Đông', 'Giảng đường A'],
  ['Bãi D', '300', '360', 'Cổng chính', 'Khu thể thao'],
]

const morning = [['Tiết 1', '07:00'], ['Tiết 2', '07:55'], ['Tiết 3', '08:50'], ['Tiết 4', '09:45']]
const afternoon = [['Tiết 5', '12:30'], ['Tiết 6', '13:25'], ['Tiết 7', '14:20'], ['Tiết 8', '15:15']]

function Field({ label, value }: { label: string; value: string }) {
  return <label className="settings-field"><span>{label}</span><input defaultValue={value} /></label>
}

function ShiftCard({ title, lessons }: { title: string; lessons: string[][] }) {
  return <section className="shift-card"><h3>{title}</h3><div className="shift-fields">{lessons.map(([label, value]) => <Field key={label} label={label} value={value} />)}</div></section>
}

export function SettingsPage() {
  return <div className="settings-page">
    <header className="settings-header"><div><h1>Cấu hình trường</h1><p>Đại học Thành Phố · Cơ sở chính</p></div><div className="settings-header-actions"><button className="text-action" type="button">Nạp cấu hình mẫu</button><button className="primary-action" type="button">Lưu cấu hình</button></div></header>
    <div className="settings-content">
      <section className="settings-summary"><Field label="Tổng số sinh viên" value="12.000" /><Field label="Tỷ lệ xe máy (%)" value="65" /></section>
      <section className="lots-section"><h2>Bãi xe · 4 bãi</h2><div className="lots-form"><div className="lots-head"><span>Bãi</span><span>Thiết kế (xe)</span><span>Tối đa (xe)</span><span>Cổng vào</span><span>Tòa nhà gần</span></div>{lots.map((lot) => <div className="lot-form-row" key={lot[0]}>{lot.map((value, index) => <input aria-label={`${lot[0]} ${['tên','thiết kế','tối đa','cổng vào','tòa nhà gần'][index]}`} defaultValue={value} key={`${lot[0]}-${value}`} />)}</div>)}</div></section>
      <div className="settings-lists"><section><h2>Danh sách cổng</h2><div><span>Cổng chính</span><span>Cổng phía Đông</span></div></section><section><h2>Danh sách tòa nhà</h2><div><span>Nhà hành chính</span><span>Thư viện</span><span>Giảng đường A</span><span>Khu thể thao</span></div></section></div>
      <section className="schedule-section"><h2>Ca học &amp; giờ bắt đầu tiết</h2><p>Theo lịch học của trường · cập nhật từ file lịch học</p><div className="shift-grid"><ShiftCard lessons={morning} title="Ca sáng" /><ShiftCard lessons={afternoon} title="Ca chiều" /></div></section>
      <section className="advanced-settings"><h2>Cấu hình nâng cao</h2><div><Field label="Giờ mở cửa" value="06:00" /><Field label="Giờ đóng cửa" value="22:00" /><Field label="Tỷ lệ xe qua đêm tối đa (%)" value="5" /></div><p>Bãi thay thế ưu tiên: A → C; B → C. Kiểm tra sức chứa và tình trạng cổng trước khi áp dụng.</p></section>
    </div>
  </div>
}
