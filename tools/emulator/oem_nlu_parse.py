#!/usr/bin/env python3
"""logcat nluResult satirindan (kirpik olabilir) kopru JSON'u uretir."""
import sys, re, json

s = sys.stdin.read()
i = s.find("nluResult:")
if i >= 0:
    s = s[i + len("nluResult:"):]

def f(k):
    m = re.search(r'"' + k + r'"\s*:\s*"([^"]*)"', s)
    return m.group(1) if m else ""

name = ""
m = re.search(r'"semantic"\s*:\s*\{\s*"slots"\s*:\s*\{([^}]*)\}', s)
if m:
    n = re.search(r'"name"\s*:\s*"([^"]*)"', m.group(1))
    if n:
        name = n.group(1)

service = f("service") or "carControl"
intent = f("intent")
operation = f("operation")
normal = f("normal_text")
sem = {"service": service}
if intent:
    sem["intent"] = intent
if operation:
    sem["operation"] = operation
if name:
    sem["slots"] = {"name": name}
print(json.dumps({"focus": "carControl" if service == "carControl" else service,
                  "semantic": sem, "content": normal}, ensure_ascii=False))
