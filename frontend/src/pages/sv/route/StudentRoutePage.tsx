import { Link, Navigate } from 'react-router-dom'
import { PlanChangeBanner } from '../components/PlanChangeBanner'
import { StudentCampusMap } from '../components/StudentCampusMap'
import { useStudentFlow } from '../state/StudentFlowContext'
import { isSafeMapsUrl } from './mapsUrl'
export function StudentRoutePage() {
  const { selection, config, recommendation, loading, error, retry } = useStudentFlow()
  if (!selection) return <Navigate replace to="/sv" />
  if (loading || !config || (!recommendation && !error)) return <div className="student-loading" aria-busy="true">Đang chuẩn bị chỉ đường...</div>
  if (error || !recommendation) return <div className="student-error"><p>{error}</p><button onClick={retry}>Thử lại</button></div>
  const building = config.buildings.find((item) => item.id === selection.building), safeMaps = isSafeMapsUrl(recommendation.route.mapsUrl)
  return <section className="student-screen route-screen"><Link className="student-back" to="/sv/recommendation">← Quay lại gợi ý</Link><p className="eyebrow">Chỉ đường tới {recommendation.lotName}</p><h1>Đi theo 2 chặng</h1><p className="route-intro">Qua đúng cổng rồi theo tuyến nội khu được tô xanh.</p><PlanChangeBanner message={recommendation.changed ? recommendation.changeMessage : null}/><article className="route-card"><span className="step-number">1</span><div><h2>Đến cổng trường</h2><strong>{recommendation.gateName}</strong><p>Mở từ {recommendation.openFrom}</p></div>{safeMaps ? <a className="maps-link" href={recommendation.route.mapsUrl} target="_blank" rel="noreferrer">Mở Google Maps ↗</a> : <button className="maps-link" disabled>Link Maps không hợp lệ</button>}</article><article className="student-card route-campus"><div className="route-title"><span className="step-number">2</span><div><h2>Trong khuôn viên</h2><p>Từ {recommendation.gateName} tới {recommendation.lotName}</p></div></div><StudentCampusMap lots={config.lots} highlightedLotId={recommendation.lotId} showRoute/><ol>{recommendation.route.steps.map((step) => <li key={step}>{step}</li>)}</ol></article><div className="walk-card"><span>⌁</span><p>Đi bộ khoảng <strong>{recommendation.route.walkMinutes} phút</strong> từ {recommendation.lotName} tới {building?.name}</p></div><Link className="student-secondary" to="/sv/recommendation">Xem lại gợi ý</Link><Link className="student-text-link" to="/sv">Đổi nơi học hoặc giờ đến</Link></section>
}
