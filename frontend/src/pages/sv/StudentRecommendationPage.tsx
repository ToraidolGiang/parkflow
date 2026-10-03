import { Link, Navigate } from 'react-router-dom'
import { PlanChangeBanner } from './components/PlanChangeBanner'
import { StudentCampusMap } from './components/StudentCampusMap'
import { useStudentFlow } from './state/StudentFlowContext'

export function StudentRecommendationPage() {
  const { selection, config, recommendation, loading, error, retry } = useStudentFlow()
  if (!selection) return <Navigate replace to="/sv" />
  if (loading || !config || (!recommendation && !error)) return <div className="student-loading" aria-busy="true">Đang chuẩn bị gợi ý...</div>
  if (error || !recommendation) return <div className="student-error"><strong>Chưa thể tải gợi ý</strong><p>{error}</p><button onClick={retry}>Thử lại</button><Link to="/sv">Đổi nơi học</Link></div>
  const building = config.buildings.find((item) => item.id === selection.building)
  return <section className="student-screen recommendation-screen"><Link className="student-back" to="/sv">← Thay đổi</Link><p className="eyebrow">Gợi ý cho {building?.name} · đến {selection.arrive}</p><PlanChangeBanner message={recommendation.changed ? recommendation.changeMessage : null}/><article className="recommendation-hero"><span className="hero-label">Phương án phù hợp nhất</span><h1>Nên gửi {recommendation.lotName}</h1><p className="gate-callout">Vào {recommendation.gateName} từ {recommendation.openFrom}</p><div className="occupancy-pill"><strong>{recommendation.expectedPct}%</strong><span>còn nhận xe theo kế hoạch</span></div><p className="reason"><span>Vì sao?</span> {recommendation.reason}</p></article><section className="student-card"><div className="card-heading"><div><h2>Sơ đồ khuôn viên</h2><p>Bãi phù hợp đã được đánh dấu</p></div><span>Trực tiếp</span></div><StudentCampusMap lots={config.lots} highlightedLotId={recommendation.lotId}/></section><div className="plan-meta"><span>Cập nhật {recommendation.updatedAt}</span><strong>✓ Kế hoạch #{recommendation.planVersion} đã duyệt</strong></div><Link className="student-primary" to="/sv/route">Chỉ đường tới {recommendation.lotName} <span>→</span></Link><Link className="student-secondary" to="/sv">Đổi nơi học hoặc giờ đến</Link></section>
}
