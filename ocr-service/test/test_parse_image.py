import argparse
import json
from pathlib import Path

import requests


def main() -> None:
    parser = argparse.ArgumentParser(description="Call /parse-arena with an image file.")
    parser.add_argument("image", type=Path, help="Path to arena screenshot")
    parser.add_argument("--url", default="http://127.0.0.1:8000/parse-arena", help="API endpoint URL")
    args = parser.parse_args()

    if not args.image.exists():
        raise SystemExit(f"Image not found: {args.image}")

    with args.image.open("rb") as f:
        files = {"file": (args.image.name, f, "image/png")}
        resp = requests.post(args.url, files=files, timeout=60)

    print("status:", resp.status_code)
    try:
        print(json.dumps(resp.json(), ensure_ascii=False, indent=2))
    except Exception:
        print(resp.text)


if __name__ == "__main__":
    main()

