-- 草稿会话表：保存当前会话快照与过期时间
CREATE TABLE IF NOT EXISTS arena_draft_sessions (
    session_id VARCHAR(64) PRIMARY KEY,
    payload JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NULL,
    INDEX idx_arena_draft_sessions_expires_at (expires_at),
    INDEX idx_arena_draft_sessions_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 异步分析任务表：保存任务状态、结果与过期时间
CREATE TABLE IF NOT EXISTS arena_analyze_jobs (
    job_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NULL,
    cache_key VARCHAR(512) NULL,
    status VARCHAR(32) NOT NULL,
    payload JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NULL,
    INDEX idx_arena_analyze_jobs_status_updated_at (status, updated_at),
    INDEX idx_arena_analyze_jobs_session_status (session_id, status),
    INDEX idx_arena_analyze_jobs_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分析结果缓存表：保存可复用的分析响应
CREATE TABLE IF NOT EXISTS arena_analyze_result_cache (
    cache_key VARCHAR(512) PRIMARY KEY,
    payload JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NULL,
    INDEX idx_arena_analyze_result_cache_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- RAG 召回事件表：记录每次知识库检索请求的上下文与整体结果
CREATE TABLE IF NOT EXISTS arena_rag_retrieval_events (
    event_id VARCHAR(64) PRIMARY KEY,
    analysis_id VARCHAR(64) NULL,
    session_id VARCHAR(64) NULL,
    tool_name VARCHAR(64) NOT NULL,
    query_text TEXT NULL,
    tags_json JSON NULL,
    keywords_json JSON NULL,
    requested_limit INT NOT NULL,
    retrieved_count INT NOT NULL,
    duration_ms BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_arena_rag_events_created_at (created_at),
    INDEX idx_arena_rag_events_analysis_id (analysis_id),
    INDEX idx_arena_rag_events_session_created_at (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- RAG 召回明细表：记录每次召回命中的具体知识文档
CREATE TABLE IF NOT EXISTS arena_rag_retrieval_hits (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    rank_no INT NOT NULL,
    document_id VARCHAR(128) NULL,
    collection VARCHAR(128) NULL,
    title VARCHAR(512) NULL,
    score DOUBLE NULL,
    snippet TEXT NULL,
    source_title VARCHAR(512) NULL,
    source_url TEXT NULL,
    tags_json JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_arena_rag_hits_event_rank (event_id, rank_no),
    INDEX idx_arena_rag_hits_collection_created_at (collection, created_at),
    INDEX idx_arena_rag_hits_document_created_at (document_id, created_at),
    CONSTRAINT fk_arena_rag_hits_event
        FOREIGN KEY (event_id)
        REFERENCES arena_rag_retrieval_events(event_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
