import json, sys
from pathlib import Path

def main():
  p = Path(__file__).resolve().parents[1] / "registry" / "formulas.json"
  data = json.loads(p.read_text(encoding="utf-8"))
  assert "categories" in data and "formulas" in data, "Missing keys"
  cat_ids = {c["id"] for c in data["categories"]}
  ids = set()
  for f in data["formulas"]:
    fid = f["id"]
    assert fid not in ids, f"Duplicate formula id: {fid}"
    ids.add(fid)
    assert f["category"] in cat_ids, f"Unknown category for {fid}: {f['category']}"
    assert f.get("name"), f"Missing name for {fid}"
    assert f.get("status") in {"implemented","planned","experimental","deprecated"}, f"Bad status for {fid}"
  print("Registry OK. formulas:", len(data["formulas"]), "categories:", len(cat_ids))

if __name__ == "__main__":
  try:
    main()
  except Exception as e:
    print("Registry invalid:", e)
    sys.exit(1)
