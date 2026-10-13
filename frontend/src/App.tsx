import { useEffect, useState } from 'react'
import './App.css'

interface HealthData {
  status: string
  database: string
  appInfoCount: number
  error?: string
}

interface ApiResponse {
  success: boolean
  data?: HealthData
  message?: string
  error?: {
    code: string
    message: string
  }
  timestamp: string
}

function App() {
  const [health, setHealth] = useState<HealthData | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [lastChecked, setLastChecked] = useState<string>('')

  const fetchHealth = async () => {
    setLoading(true)
    setErrorMsg(null)
    try {
      const res = await fetch('/api/health')
      const json: ApiResponse = await res.json()
      if (json.success && json.data) {
        setHealth(json.data)
      } else {
        setHealth(json.data || null)
        setErrorMsg(json.error?.message || 'Không thể kết nối đến cơ sở dữ liệu')
      }
    } catch (err: unknown) {
      setHealth(null)
      setErrorMsg(
        err instanceof Error
          ? err.message
          : 'Backend chưa khởi động hoặc gặp lỗi mạng (Hãy chạy: cd backend && mvn spring-boot:run)'
      )
    } finally {
      setLoading(false)
      setLastChecked(new Date().toLocaleTimeString())
    }
  }

  useEffect(() => {
    fetchHealth()
  }, [])

  return (
    <div className="container">
      {/* Header */}
      <header className="header">
        <div className="badge">GIAI ĐOẠN 3 — PROJECT FOUNDATION</div>
        <h1 className="title">Grain Warehouse System</h1>
        <p className="subtitle">
          Hệ thống quản lý chuỗi phân phối cám & kho hàng đa cấp
        </p>
      </header>

      {/* Main Status Grid */}
      <main className="status-grid">
        {/* Frontend Card */}
        <div className="card">
          <div className="card-header">
            <span className="card-icon">⚡</span>
            <div>
              <h3>Frontend</h3>
              <p>React 18 + TypeScript + Vite</p>
            </div>
            <span className="status-pill status-up">ONLINE</span>
          </div>
          <div className="card-body">
            <div className="metric-row">
              <span>Dev Server:</span>
              <code>http://localhost:5173</code>
            </div>
            <div className="metric-row">
              <span>Vite Proxy:</span>
              <code>/api ➔ :8080</code>
            </div>
            <div className="metric-row">
              <span>UI Framework:</span>
              <span>Vanilla Modern CSS</span>
            </div>
          </div>
        </div>

        {/* Backend Card */}
        <div className="card">
          <div className="card-header">
            <span className="card-icon">☕</span>
            <div>
              <h3>Backend</h3>
              <p>Java 21 + Spring Boot 3.3.5</p>
            </div>
            <span
              className={`status-pill ${
                health?.status === 'UP'
                  ? 'status-up'
                  : loading
                  ? 'status-loading'
                  : 'status-down'
              }`}
            >
              {loading ? 'CHECKING...' : health?.status === 'UP' ? 'CONNECTED' : 'OFFLINE'}
            </span>
          </div>
          <div className="card-body">
            <div className="metric-row">
              <span>Port:</span>
              <code>8080</code>
            </div>
            <div className="metric-row">
              <span>Actuator:</span>
              <code>/actuator/health</code>
            </div>
            <div className="metric-row">
              <span>API Health:</span>
              <code>/api/health</code>
            </div>
          </div>
        </div>

        {/* Database Card */}
        <div className="card">
          <div className="card-header">
            <span className="card-icon">🐘</span>
            <div>
              <h3>PostgreSQL & Flyway</h3>
              <p>Docker Compose / Local DB</p>
            </div>
            <span
              className={`status-pill ${
                health?.database === 'CONNECTED'
                  ? 'status-up'
                  : loading
                  ? 'status-loading'
                  : 'status-down'
              }`}
            >
              {loading
                ? 'CHECKING...'
                : health?.database === 'CONNECTED'
                ? 'CONNECTED'
                : 'DISCONNECTED'}
            </span>
          </div>
          <div className="card-body">
            <div className="metric-row">
              <span>Flyway V1 Migration:</span>
              <span>
                {health?.database === 'CONNECTED' ? '✅ Initialized (app_info)' : 'Chưa áp dụng'}
              </span>
            </div>
            <div className="metric-row">
              <span>App Records:</span>
              <code>{health ? `${health.appInfoCount} row(s)` : '---'}</code>
            </div>
            <div className="metric-row">
              <span>Container Port:</span>
              <code>5432</code>
            </div>
          </div>
        </div>
      </main>

      {/* Interactive Controls & Alert */}
      <section className="action-section">
        <button
          className="btn-refresh"
          onClick={fetchHealth}
          disabled={loading}
          type="button"
        >
          {loading ? 'Đang kiểm tra...' : '🔄 Kiểm tra lại kết nối'}
        </button>
        {lastChecked && (
          <span className="timestamp-text">Kiểm tra lần cuối: {lastChecked}</span>
        )}

        {errorMsg && (
          <div className="error-banner">
            <strong>⚠️ Lưu ý kết nối Backend:</strong> {errorMsg}
            <div className="help-text">
              Hãy đảm bảo Docker PostgreSQL đang chạy (<code>docker compose -f infra/docker/docker-compose.dev.yml up -d</code>)
              và Backend đã start (<code>cd backend && mvn spring-boot:run</code>).
            </div>
          </div>
        )}
      </section>

      {/* Specifications & Architecture Checklist */}
      <section className="info-box">
        <h3>📋 Tiêu chí Foundation đã hoàn thành</h3>
        <ul className="checklist">
          <li>✅ Git Repository khởi tạo & commit cấu trúc ban đầu</li>
          <li>✅ Tái cấu trúc bộ tài liệu theo chuyên đề (Business, Architecture, API, Maintenance)</li>
          <li>✅ Thiết lập chuẩn giao tiếp API FE ↔ BE (Envelope <code>ApiResponse</code> & Error spec)</li>
          <li>✅ Spring Boot 3.3.5 (Java 21 LTS) + JPA + Flyway + Actuator</li>
          <li>✅ React 18 + TypeScript + Vite + Dev Server Proxy</li>
          <li>✅ Docker Compose cho PostgreSQL Dev</li>
          <li>✅ Smoke tests unit & integration controller đã vượt qua (BUILD SUCCESS)</li>
        </ul>
      </section>
    </div>
  )
}

export default App
