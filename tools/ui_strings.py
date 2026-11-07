#!/usr/bin/env python3
"""Saca todos los textos en inglés de los pares bilingües (tr / L.t / busy / toast / ...)
y los guarda en app/src/main/assets/ui_strings.txt para traducir la app a otros idiomas."""
import os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java")
OUT = os.path.join(ROOT, "app/src/main/assets/ui_strings.txt")
NAMES = {"tr", "t", "busy", "toast", "bind", "bindHint", "pill", "key", "moveKey", "command", "ask"}

def tokens(s):
    i, n = 0, len(s)
    while i < n:
        c = s[i]
        if s.startswith("//", i):
            j = s.find("\n", i); i = n if j < 0 else j; continue
        if s.startswith("/*", i):
            j = s.find("*/", i + 2); i = n if j < 0 else j + 2; continue
        if s.startswith('"""', i):
            j = s.find('"""', i + 3); j = n if j < 0 else j + 3
            yield ("S", None); i = j; continue
        if c == '"':
            i += 1; buf = []; tmpl = False
            while i < n and s[i] != '"':
                ch = s[i]
                if ch == "\\":
                    e = s[i + 1]
                    if e == "n": buf.append("\n")
                    elif e == "t": buf.append("\t")
                    elif e == "u": buf.append(chr(int(s[i + 2:i + 6], 16))); i += 4
                    else: buf.append(e)
                    i += 2; continue
                if ch == "$" and i + 1 < n and (s[i + 1] == "{" or s[i + 1].isalpha() or s[i + 1] == "_"):
                    tmpl = True
                    if s[i + 1] == "{":
                        d = 0
                        while i < n:
                            if s[i] == "{": d += 1
                            elif s[i] == "}":
                                d -= 1
                                if d == 0: break
                            i += 1
                    i += 1; continue
                buf.append(ch); i += 1
            i += 1
            yield ("S", None if tmpl else "".join(buf)); continue
        if c == "'":
            j = i + 1
            while j < n and s[j] != "'":
                j += 2 if s[j] == "\\" else 1
            i = j + 1; continue
        if c.isalpha() or c == "_":
            j = i
            while j < n and (s[j].isalnum() or s[j] == "_"): j += 1
            yield ("I", s[i:j]); i = j; continue
        if c.isspace(): i += 1; continue
        yield ("P", c); i += 1

def literal(arg):
    if not arg or len(arg) % 2 == 0: return None
    out = []
    for k, t in enumerate(arg):
        if k % 2 == 0:
            if t[0] != "S" or t[1] is None: return None
            out.append(t[1])
        elif t != ("P", "+"): return None
    return "".join(out)

found = set()
for dp, _, fs in os.walk(SRC):
    for f in fs:
        if not f.endswith(".kt"): continue
        toks = list(tokens(open(os.path.join(dp, f), encoding="utf-8").read()))
        for i in range(len(toks) - 1):
            if toks[i][0] == "I" and toks[i][1] in NAMES and toks[i + 1] == ("P", "("):
                args, cur, d, j = [], [], 0, i + 2
                while j < len(toks):
                    t = toks[j]
                    if t[0] == "P" and t[1] in "([{": d += 1
                    if t[0] == "P" and t[1] in ")]}":
                        if d == 0: break
                        d -= 1
                    if d == 0 and t == ("P", ","): args.append(cur); cur = []
                    else: cur.append(t)
                    j += 1
                args.append(cur)
                lits = [literal(a) for a in args]
                for a, b in zip(lits, lits[1:]):
                    if a is not None and b is not None:
                        if b.strip() and re.search(r"[A-Za-z]", b): found.add(b)
                        break

def esc(x): return x.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as fh:
    for x in sorted(found): fh.write(esc(x) + "\n")
print(len(found), "textos")
