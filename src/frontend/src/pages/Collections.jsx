import { useEffect, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { listCollections, createCollection, deleteCollection, ApiError } from '../mock/api'
import { toast } from '../components/Toast'

export function Collections() {
  const { user } = useAuth()
  const [collections, setCollections] = useState(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [creating, setCreating] = useState(false)

  const load = useCallback(async () => {
    setCollections(await listCollections(user.id))
  }, [user.id])

  useEffect(() => {
    load()
  }, [load])

  async function handleCreate(e) {
    e.preventDefault()
    setCreating(true)
    try {
      await createCollection(user.id, name.trim(), description.trim())
      setName('')
      setDescription('')
      toast('컬렉션을 만들었습니다.', 'success')
      load()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '오류가 발생했습니다.', 'error')
    } finally {
      setCreating(false)
    }
  }

  async function handleDelete(c) {
    if (!confirm(`'${c.name}' 컬렉션과 안의 북마크를 모두 삭제할까요?`)) return
    await deleteCollection(c.id, user.id)
    toast('컬렉션을 삭제했습니다.', 'success')
    load()
  }

  return (
    <div className="page">
      <h1>컬렉션</h1>
      <p className="hero__sub">
        여러 링크를 묶어 관리하는 북마크 모음입니다 (<code>collections</code> /{' '}
        <code>bookmarks</code> / <code>tags</code> 테이블).
      </p>

      <form className="card inline-form" onSubmit={handleCreate}>
        <input
          type="text"
          placeholder="컬렉션 이름"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
          maxLength={100}
        />
        <input
          type="text"
          placeholder="설명 (선택)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={500}
        />
        <button className="btn btn--primary" disabled={creating}>
          {creating ? '생성 중...' : '+ 만들기'}
        </button>
      </form>

      {collections === null ? (
        <p>불러오는 중...</p>
      ) : collections.length === 0 ? (
        <div className="card empty-state">아직 컬렉션이 없습니다.</div>
      ) : (
        <div className="collection-grid">
          {collections.map((c) => (
            <div key={c.id} className="card collection-card">
              <Link to={`/collections/${c.id}`} className="collection-card__title">
                {c.name}
              </Link>
              {c.description && <p className="collection-card__desc">{c.description}</p>}
              <div className="collection-card__actions">
                <Link to={`/collections/${c.id}`} className="btn btn--ghost btn--sm">
                  열기
                </Link>
                <button className="btn btn--danger btn--sm" onClick={() => handleDelete(c)}>
                  삭제
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
