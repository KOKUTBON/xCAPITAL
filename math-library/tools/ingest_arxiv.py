"""Continuous ingestion job: arXiv metadata -> paper inbox.

This script is designed to run in GitHub Actions (has internet).
It:
- loads ingestion config
- queries arXiv API (Atom)
- writes new paper metadata JSON into math-library/papers/inbox/
- deduplicates by arXiv id
- optionally creates stub plugin files (disabled by default, can enable)
"""

from __future__ import annotations
import json, os, re, sys, time
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Dict, List, Any

import requests
import yaml
import feedparser

@dataclass
class Query:
    name: str
    q: str
    tags: List[str]

def load_config(repo_root: Path) -> Dict[str, Any]:
    cfg_path = repo_root / "math-library" / "ingestion" / "config.yaml"
    return yaml.safe_load(cfg_path.read_text(encoding="utf-8"))

def arxiv_id_from_entry(entry: dict) -> str:
    # arXiv ID often appears at end of id field: http://arxiv.org/abs/XXXX.XXXXXvY
    raw = entry.get("id","")
    m = re.search(r"/abs/([^/]+)$", raw)
    return m.group(1) if m else raw.rsplit("/",1)[-1]

def normalize_authors(entry: dict) -> List[str]:
    authors = entry.get("authors", [])
    out = []
    for a in authors:
        name = a.get("name")
        if name: out.append(name)
    return out

def main():
    repo_root = Path(__file__).resolve().parents[2]  # repository root
    cfg = load_config(repo_root)

    arxiv = cfg["sources"]["arxiv"]
    if not arxiv.get("enabled", True):
        print("arXiv ingestion disabled.")
        return

    base_url = arxiv["base_url"]
    lookback_days = int(arxiv.get("lookback_days", 7))
    cutoff = datetime.now(timezone.utc) - timedelta(days=lookback_days)

    out_dir = repo_root / cfg["outputs"]["papers_inbox_dir"]
    out_dir.mkdir(parents=True, exist_ok=True)

    # Build queries
    queries = [Query(**q) for q in arxiv["queries"]]
    max_results = int(arxiv.get("max_results_per_query", 50))
    sort_by = arxiv.get("sort_by","submittedDate")
    sort_order = arxiv.get("sort_order","descending")

    # Load existing IDs
    existing = set()
    for p in out_dir.glob("*.json"):
        try:
            j = json.loads(p.read_text(encoding="utf-8"))
            if "arxiv_id" in j:
                existing.add(j["arxiv_id"])
        except Exception:
            pass

    new_count = 0
    session = requests.Session()
    session.headers.update({"User-Agent": "xCAPITAL-IngestionBot/0.1 (mailto:devnull@example.com)"})

    for q in queries:
        params = {
            "search_query": q.q,
            "start": 0,
            "max_results": max_results,
            "sortBy": sort_by,
            "sortOrder": sort_order,
        }
        print(f"Query {q.name}: {q.q}")
        resp = session.get(base_url, params=params, timeout=30)
        resp.raise_for_status()

        feed = feedparser.parse(resp.text)
        for entry in feed.entries:
            aid = arxiv_id_from_entry(entry)
            if aid in existing:
                continue

            # parse updated/published time
            published = entry.get("published") or entry.get("updated")
            try:
                dt = datetime.fromisoformat(published.replace("Z","+00:00"))
            except Exception:
                dt = datetime.now(timezone.utc)

            if dt < cutoff:
                # older than lookback window
                continue

            paper = {
                "id": f"arxiv_{aid.replace('.', '_')}",
                "source": "arxiv",
                "arxiv_id": aid,
                "title": entry.get("title","").strip().replace("\n"," "),
                "authors": normalize_authors(entry),
                "published_at": dt.isoformat(),
                "updated_at": (entry.get("updated") or published),
                "summary": (entry.get("summary","") or "").strip(),
                "tags": list(set(q.tags + ["arxiv"])),
                "categories": entry.get("arxiv_primary_category", {}).get("term") if isinstance(entry.get("arxiv_primary_category"), dict) else None,
                "links": [l.get("href") for l in entry.get("links", []) if l.get("href")],
            }

            # file name is arxiv_id.json
            fn = out_dir / f"{aid.replace('/','_')}.json"
            fn.write_text(json.dumps(paper, indent=2, ensure_ascii=False), encoding="utf-8")
            existing.add(aid)
            new_count += 1

        time.sleep(1.0)  # be nice

    print("New papers added:", new_count)

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print("Ingestion failed:", e)
        sys.exit(1)
