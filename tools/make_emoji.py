"""Genera app/src/main/assets/emoji.txt desde emoji.json de iamcal/emoji-data (licencia MIT).
https://github.com/iamcal/emoji-data
Uso: python3 tools/make_emoji.py emoji.json
Formato: categoria<TAB>emoji<TAB>tonos de piel separados por espacio<TAB>nombre
"""
import json
import sys

CATS = ["Smileys & Emotion", "People & Body", "Animals & Nature", "Food & Drink",
        "Travel & Places", "Activities", "Objects", "Symbols", "Flags"]
TONES = ["1F3FB", "1F3FC", "1F3FD", "1F3FE", "1F3FF"]


def chars(unified):
    return "".join(chr(int(p, 16)) for p in unified.split("-"))


def main(path):
    data = json.load(open(path, encoding="utf-8"))
    data.sort(key=lambda e: e["sort_order"])
    out = []
    for e in data:
        if e["category"] not in CATS:
            continue
        cat = CATS.index(e["category"])
        tones = []
        sv = e.get("skin_variations") or {}
        for t in TONES:
            v = sv.get(t) or sv.get(f"{t}-{t}")
            if v:
                tones.append(chars(v["unified"]))
        name = (e.get("short_name") or "").replace("_", " ")
        out.append(f"{cat}\t{chars(e['unified'])}\t{' '.join(tones)}\t{name}")
    with open("app/src/main/assets/emoji.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print("emojis:", len(out))


if __name__ == "__main__":
    main(sys.argv[1])
