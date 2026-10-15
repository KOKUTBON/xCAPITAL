"""Generate formula + plugin stubs from a paper metadata entry.

Usage:
  python math-library/tools/generate_formula_stub.py math-library/papers/inbox/<paper>.json

This does NOT magically implement the paper. It scaffolds:
- a registry entry with references and placeholders
- Java and Python plugin stubs
"""

from __future__ import annotations
import json, re, sys
from pathlib import Path
from datetime import datetime, timezone

def slugify(s: str) -> str:
    s = s.lower()
    s = re.sub(r"[^a-z0-9]+", "_", s).strip("_")
    return s[:64] if len(s) > 64 else s

def load_registry(reg_path: Path):
    return json.loads(reg_path.read_text(encoding="utf-8"))

def save_registry(reg_path: Path, reg):
    reg_path.write_text(json.dumps(reg, indent=2, ensure_ascii=False), encoding="utf-8")

def main():
    if len(sys.argv) < 2:
        raise SystemExit("Provide paper JSON path")

    repo_root = Path(__file__).resolve().parents[2]
    cfg_path = repo_root / "math-library" / "ingestion" / "config.yaml"
    try:
        import yaml
        cfg = yaml.safe_load(cfg_path.read_text(encoding="utf-8"))
    except Exception:
        raise SystemExit("PyYAML required")

    paper_path = (repo_root / sys.argv[1]).resolve()
    paper = json.loads(paper_path.read_text(encoding="utf-8"))

    title = paper.get("title","Untitled")
    fid = slugify(title)
    formula_id = f"paper_{fid}"

    reg_path = repo_root / cfg["outputs"]["registry_path"]
    reg = load_registry(reg_path)

    # Prevent duplicates
    if any(f.get("id") == formula_id for f in reg.get("formulas", [])):
        print("Already exists in registry:", formula_id)
        return

    entry = {
        "id": formula_id,
        "category": "return_models",
        "name": title,
        "status": "planned",
        "implementations": [
            {"type":"java","path":f"math-library/plugins/java/generated/{formula_id}.java"},
            {"type":"python","path":f"math-library/plugins/python/generated/{formula_id}.py"},
        ],
        "parameters": [],
        "assumptions": [],
        "references": [
            {"type":"paper","citation": title, "source":"arxiv", "arxiv_id": paper.get("arxiv_id"), "links": paper.get("links", [])},
        ],
        "created_at": datetime.now(timezone.utc).isoformat()
    }

    reg.setdefault("formulas", []).append(entry)
    save_registry(reg_path, reg)
    print("Added registry entry:", formula_id)

    # Generate stubs
    java_dir = repo_root / cfg["outputs"]["stubs_dir_java"]
    py_dir = repo_root / cfg["outputs"]["stubs_dir_python"]
    java_dir.mkdir(parents=True, exist_ok=True)
    py_dir.mkdir(parents=True, exist_ok=True)

    java_code = f'''package com.alphamath.portfolio.mathlib.generated;

import com.alphamath.portfolio.mathlib.FormulaPlugin;
import java.util.Map;

/**
 * Auto-generated stub for: {title}
 * Source: arXiv {paper.get("arxiv_id")}
 *
 * TODO:
 * - Translate paper math into code
 * - Validate inputs
 * - Add unit tests
 */
public class {formula_id} implements FormulaPlugin {{

  @Override public String id() {{ return "{formula_id}"; }}
  @Override public String name() {{ return "{title.replace('"','\\\"')}"; }}

  @Override
  public Map<String, Object> evaluate(Map<String, Object> inputs) {{
    return Map.of(
      "status", "planned",
      "message", "Stub generated. Implement the paper's method here.",
      "paper", "{paper.get("arxiv_id","")}"
    );
  }}
}}
'''
    (java_dir / f"{formula_id}.java").write_text(java_code, encoding="utf-8")

    py_code = f'''from math_library.plugins.python.plugin_base import FormulaPlugin

class {formula_id}(FormulaPlugin):
    @property
    def id(self) -> str:
        return "{formula_id}"

    @property
    def name(self) -> str:
        return "{title.replace('"','\\\"')}"

    def evaluate(self, inputs):
        return {{
            "status": "planned",
            "message": "Stub generated. Implement the paper's method here.",
            "paper": "{paper.get("arxiv_id","")}",
        }}
'''
    (py_dir / f"{formula_id}.py").write_text(py_code, encoding="utf-8")

    print("Generated stubs:", java_dir / f"{formula_id}.java", "and", py_dir / f"{formula_id}.py")

if __name__ == "__main__":
    main()
