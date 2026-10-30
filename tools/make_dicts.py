"""Genera los diccionarios del teclado (app/src/main/assets/dict_*.txt).

Fuente: FrequencyWords de Hermit Dave (OpenSubtitles 2018), licencia CC-BY-SA 4.0.
https://github.com/hermitdave/FrequencyWords

Uso: python3 tools/make_dicts.py es_50k.txt en_50k.txt
Formato de salida: una palabra por línea, "palabra<TAB>frecuencia",
ordenadas por su forma "plegada" (sin acentos ni apóstrofos), igual que TextUtil.fold en Kotlin.
"""
import re
import sys

FOLD = str.maketrans({
    'à': 'a', 'á': 'a', 'â': 'a', 'ã': 'a', 'ä': 'a', 'å': 'a',
    'è': 'e', 'é': 'e', 'ê': 'e', 'ë': 'e',
    'ì': 'i', 'í': 'i', 'î': 'i', 'ï': 'i',
    'ò': 'o', 'ó': 'o', 'ô': 'o', 'õ': 'o', 'ö': 'o',
    'ù': 'u', 'ú': 'u', 'û': 'u', 'ü': 'u',
    'ñ': 'n', 'ç': 'c', 'ý': 'y', 'ÿ': 'y',
    "'": None, '’': None,
})


def fold(w: str) -> str:
    return w.lower().translate(FOLD)


def read(path):
    out = {}
    for line in open(path, encoding='utf-8'):
        parts = line.split()
        if len(parts) != 2:
            continue
        w, c = parts[0], int(parts[1])
        out[w] = out.get(w, 0) + c
    return out


TARGET_TOTAL = 100_000_000


def normalize(words):
    """Pone los dos idiomas en la misma escala (como si cada uno tuviera 100 millones de palabras)."""
    total = sum(words.values())
    return {w: max(1, round(c * TARGET_TOTAL / total)) for w, c in words.items()}


KEEP = {"té", "dé", "sé", "tú", "él", "mí", "sí", "más", "aún", "qué", "cómo", "dónde", "cuándo", "quién"}


def drop_misspellings(words, ratio=12):
    """Quita faltas de ortografía comunes en subtítulos ("tambien") cuando la forma correcta
    ("también") es al menos [ratio] veces más común. "esta"/"está" se quedan las dos."""
    best = {}
    for w, c in words.items():
        f = fold(w)
        if f not in best or c > words[best[f]]:
            best[f] = w
    out = {}
    removed = []
    for w, c in words.items():
        b = best[fold(w)]
        if b != w and words[b] >= c * ratio and w not in KEEP:
            removed.append(w)
            continue
        out[w] = c
    print("  faltas quitadas:", len(removed), "ej.", sorted(removed, key=lambda x: -words[x])[:15])
    return out


def write(words, path):
    rows = sorted(words.items(), key=lambda kv: (fold(kv[0]), -kv[1]))
    with open(path, 'w', encoding='utf-8') as f:
        for w, c in rows:
            f.write(f"{w}\t{c}\n")
    print(path, len(rows))


def spanish(path):
    ok = re.compile(r"^[a-záéíóúüñ]+$")
    singles = set("aeouy")
    words = {}
    for w, c in read(path).items():
        if not ok.match(w):
            continue
        if len(w) == 1 and w not in singles:
            continue
        if w in EN_REMOVE or (w in EN_FRAGMENTS and w not in ("y", "don")):  # restos de inglés ("im", "dont")
            continue
        words[w] = c
    return drop_misspellings(words)


# Formas sin apóstrofo que NO son palabras reales: se quitan para que el autocorrector las arregle
EN_REMOVE = {"im", "dont", "cant", "wont", "didnt", "doesnt", "isnt", "wasnt", "arent",
             "werent", "couldnt", "wouldnt", "shouldnt", "havent", "hasnt", "hadnt",
             "youre", "theyre", "thats", "whats", "theres", "heres", "whos", "wheres",
             "hows", "hes", "shes", "youll", "theyll", "itll", "ive", "youve", "weve",
             "theyve", "youd", "theyd", "aint", "yall", "maam", "oclock"}


# Los subtítulos parten "don't" en "don" + "t": estos fragmentos se quitan
EN_FRAGMENTS = {"don", "didn", "doesn", "isn", "wasn", "couldn", "aren", "wouldn",
                "shouldn", "haven", "hasn", "hadn", "weren", "ain", "y"}

# Frecuencia estimada de cada contracción: (palabra de referencia, fracción)
EN_ESTIMATE = {
    "don't": ("don", 1.0), "didn't": ("didn", 1.0), "doesn't": ("doesn", 1.0),
    "isn't": ("isn", 1.0), "wasn't": ("wasn", 1.0), "couldn't": ("couldn", 1.0),
    "aren't": ("aren", 1.0), "wouldn't": ("wouldn", 1.0), "shouldn't": ("shouldn", 1.0),
    "haven't": ("haven", 1.0), "hasn't": ("hasn", 1.0), "hadn't": ("hadn", 1.0),
    "weren't": ("weren", 1.0), "won't": ("won", 0.6), "can't": ("can", 0.3),
    "ain't": ("ain", 1.0), "I'm": ("i", 0.12), "it's": ("it", 0.15),
    "that's": ("that", 0.12), "you're": ("you", 0.04), "we're": ("we", 0.08),
    "they're": ("they", 0.1), "what's": ("what", 0.08), "there's": ("there", 0.1),
    "here's": ("here", 0.03), "who's": ("who", 0.04), "where's": ("where", 0.04),
    "how's": ("how", 0.02), "let's": ("let", 0.5), "he's": ("he", 0.12),
    "she's": ("she", 0.12), "I'll": ("i", 0.03), "you'll": ("you", 0.01),
    "we'll": ("we", 0.04), "they'll": ("they", 0.02), "he'll": ("he", 0.02),
    "she'll": ("she", 0.02), "it'll": ("it", 0.005), "I've": ("i", 0.02),
    "you've": ("you", 0.01), "we've": ("we", 0.03), "they've": ("they", 0.02),
    "I'd": ("i", 0.015), "you'd": ("you", 0.005), "he'd": ("he", 0.01),
    "she'd": ("she", 0.01), "we'd": ("we", 0.01), "they'd": ("they", 0.01),
    "y'all": ("you", 0.0005), "ma'am": ("sir", 0.1), "o'clock": ("clock", 0.5),
}


def english(path):
    ok = re.compile(r"^[a-z]+$")
    raw = read(path)
    words = {}
    for w, c in raw.items():
        if not ok.match(w) or w in EN_REMOVE or w in EN_FRAGMENTS:
            continue
        if len(w) == 1 and w not in ("a", "i"):
            continue
        words[w] = c
    words["I"] = words.pop("i", 1000000)
    for full, (ref, frac) in EN_ESTIMATE.items():
        words[full] = max(int(raw.get(ref, 0) * frac), 2000)
    return words


if __name__ == '__main__':
    es_path, en_path = sys.argv[1], sys.argv[2]
    write(normalize(spanish(es_path)), 'app/src/main/assets/dict_es.txt')
    write(normalize(english(en_path)), 'app/src/main/assets/dict_en.txt')
