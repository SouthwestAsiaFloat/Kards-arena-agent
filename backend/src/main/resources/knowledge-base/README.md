# Knowledge Base

This directory stores strategy knowledge that can later be used by:

- local keyword retrieval
- RAG pipelines
- agent tool calls
- explanation generation

## Layout

```text
knowledge-base/
├── manifest.json
└── arena-strategy-guides/
    └── tieba-arena-guides.jsonl
```

## Document format

Each line in a `.jsonl` file is one independent knowledge chunk.

Recommended fields:

- `id`: stable chunk id
- `collection`: logical collection name
- `title`: short title
- `type`: document type, for example `rules`, `concept`, `heuristic`, `nation-overview`
- `stability`: `high`, `medium`, or `low`
- `sourceUrl`: original source URL
- `sourceTitle`: source title or source summary
- `summary`: one-line abstract
- `content`: retrieval body
- `tags`: retrieval tags
- `metadata`: optional structured fields for routing and filtering

## Current conventions

- Prefer stable strategy knowledge over card-pool-sensitive examples.
- If a source contains outdated card examples, keep the principle and omit the brittle examples.
- Keep each chunk focused on one concept so it can be retrieved independently.
