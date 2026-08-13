import { useEffect, useState, useCallback } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { listBookmarks, createBookmark, deleteBookmark, ApiError } from '../mock/api'
import { toast } from '../components/Toast'

export function CollectionDetail() {
  const { id } = useParams()
  const { user } = useAuth()
  const collectionId = Number(id)

  const [bookmarks, setBookmarks] = useState(null)
  const [url, setUrl] = useState('')
  const [title, setTitle] = useState('')
  const [tagsInput, setTagsInput] = useState('')
  const [creating, setCreating] = useState(false)

  const load = useCallback(async () => {
    setBookmarks(await listBookmarks(collectionId))
  }, [collectionId])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    const handler = () => load()
    window.addEventListener('linkshortener:bookmark-updated', handler)
    return () => window.removeEventListener('linkshortener:bookmark-updated', handler)
  }, [load])

  async function handleCreate(e) {
    e.preventDefault()
    setCreating(true)
    try {
      await createBookmark({
        collectionId,
        userId: user.id,
        url: url.trim(),
        title: title.trim(),
        tagNames: tagsInput.split(',').map((t) => t.trim()).filter(Boolean),
      })
      setUrl('')
      setTitle('')
      setTagsInput('')
      toast('북마크를 추가했습니다. 메타데이터 수집 중...', 'success')
      load()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '오류가 발생했습니다.', 'error')
    } finally {
      setCreating(false)
    }
  }

  async function handleDelete(bookmarkId) {
    await deleteBookmark(bookmarkId)
    toast('북마크를 삭제했습니다.', 'success')
    load()
  }

  return (
    <div className="page">
      <div className="page__header">
        <h1>컬렉션 상세</h1>
        <Link to="/collections" className="btn btn--ghost">
          ← 컬렉션 목록
        </Link>
      </div>

      <form className="card bookmark-form" onSubmit={handleCreate}>
        <div className="field-row">
          <label className="field">
            <span>URL</span>
            <input
              type="text"
              placeholder="https://example.com/article"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              required
            />
          </label>
          <label className="field">
            <span>제목 (선택)</span>
            <input type="text" value={title} onChange={(e) => setTitle(e.target.value)} />
          </label>
        </div>
        <label className="field">
          <span>태그 (쉼표로 구분)</span>
          <input
            type="text"
            placeholder="개발, 읽을거리"
            value={tagsInput}
            onChange={(e) => setTagsInput(e.target.value)}
          />
        </label>
        <button className="btn btn--primary" disabled={creating}>
          {creating ? '추가 중...' : '+ 북마크 추가'}
        </button>
      </form>

      {bookmarks === null ? (
        <p>불러오는 중...</p>
      ) : bookmarks.length === 0 ? (
        <div className="card empty-state">아직 북마크가 없습니다.</div>
      ) : (
        <div className="bookmark-list">
          {bookmarks.map((b) => (
            <div key={b.id} className="card bookmark-card">
              <div className="bookmark-card__main">
                <a href={b.url} target="_blank" rel="noreferrer" className="bookmark-card__title">
                  {b.title || b.url}
                </a>
                <span className={`badge ${b.meta_status === 'DONE' ? 'badge--good' : 'badge--soft'}`}>
                  {b.meta_status === 'PENDING' ? '메타데이터 수집 중' : '완료'}
                </span>
              </div>
              {b.description && <p className="bookmark-card__desc">{b.description}</p>}
              <div className="bookmark-card__footer">
                <div className="tag-list">
                  {b.tags.map((t) => (
                    <span key={t.id} className="tag-chip">
                      #{t.name}
                    </span>
                  ))}
                </div>
                <button className="btn btn--danger btn--sm" onClick={() => handleDelete(b.id)}>
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
