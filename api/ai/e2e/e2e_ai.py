"""End-to-end check of the AI API against the running stack (authentication, document-workspace, ai).

Run the AI service with AI_PROVIDER=mock (and AI_RATE_LIMIT_REQUESTS=200, since this fires many requests):

    cd api/docker && AI_PROVIDER=mock AI_RATE_LIMIT_REQUESTS=200 docker compose -f docker-compose.ai.yml up -d --build
    py api/ai/e2e/e2e_ai.py

It registers throw-away users, so run it only against a development stack.
"""
import json, urllib.request, urllib.error, uuid, subprocess

AUTH = 'http://localhost:8080/api/v1'; WS = 'http://localhost:8081/api/v1'; AI = 'http://localhost:8083/api/v1/ai'


def call(method, url, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={'Content-Type': 'application/json'})
    if token: req.add_header('Authorization', 'Bearer ' + token)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read()
        try: return e.code, json.loads(raw)
        except Exception: return e.code, {'raw': raw[:300]}


def multipart(url, fields, file, token):
    """POST a form with one file part; file is (filename, bytes). Returns (status, parsed JSON or SSE events)."""
    boundary = 'xxBOUNDARYxx'
    parts = b''
    for name, value in fields.items():
        parts += f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode()
    filename, content = file
    parts += (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
              f'Content-Type: application/octet-stream\r\n\r\n').encode() + content + b'\r\n'
    parts += f'--{boundary}--\r\n'.encode()
    req = urllib.request.Request(url, data=parts, method='POST', headers={
        'Content-Type': f'multipart/form-data; boundary={boundary}', 'Accept': 'text/event-stream, application/json',
        'Authorization': 'Bearer ' + token})
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read().decode()
            if 'text/event-stream' in r.headers.get('Content-Type', ''):
                events, name, data = [], None, []
                for line in raw.splitlines():
                    if line.startswith('event:'): name = line[6:].strip()
                    elif line.startswith('data:'): data.append(line[5:].strip())
                    elif line == '' and name:
                        events.append((name, json.loads(''.join(data)))); name = None; data = []
                return r.status, events
            return r.status, json.loads(raw)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try: return e.code, json.loads(raw)
        except Exception: return e.code, {'raw': raw[:300]}


def sse(url, body, token):
    """Returns (status, [(event, data)])."""
    req = urllib.request.Request(url, data=json.dumps(body).encode(), method='POST',
                                 headers={'Content-Type': 'application/json', 'Accept': 'text/event-stream', 'Authorization': 'Bearer ' + token})
    events = []
    try:
        with urllib.request.urlopen(req) as r:
            name = None; data = []
            for line in r.read().decode().splitlines():
                if line.startswith('event:'): name = line[6:].strip()
                elif line.startswith('data:'): data.append(line[5:].strip())
                elif line == '' and name:
                    events.append((name, json.loads(''.join(data)))); name = None; data = []
            return 200, events
    except urllib.error.HTTPError as e:
        raw = e.read()
        try: return e.code, json.loads(raw)
        except Exception: return e.code, {'raw': raw[:300]}


fails = 0
def ok(label, got, want):
    global fails
    good = got == want
    if not good: fails += 1
    print('OK  ' if good else 'FAIL', label, '' if good else f'got {got!r} wanted {want!r}')


def user(prefix):
    email = f'{prefix}-{uuid.uuid4().hex[:6]}@t.com'; pw = 'Passw0rd!x1'
    call('POST', AUTH + '/authentication/register', {'firstName': prefix, 'lastName': 'T', 'email': email, 'password': pw})
    subprocess.run(['docker', 'exec', 'sophia-postgres', 'psql', '-U', 'sophia', '-d', 'sophia_authentication', '-c', f"update users set email_verified=true where email='{email}'"], capture_output=True)
    return call('POST', AUTH + '/authentication/login', {'email': email, 'password': pw})[1]['data']['accessToken']


t = user('ai'); other = user('other')
w = call('POST', WS + '/workspaces', {'name': 'Estudio IA'}, t)[1]['data']['id']; base = f'{WS}/workspaces/{w}'


def doc(name, text):
    s, b = call('POST', base + '/artifacts', {'type': 'MARKDOWN_DOCUMENT', 'name': name}, t); assert s == 201, b
    d = b['data']['id']
    s, g = call('GET', base + f'/documents/{d}/content', None, t)
    s, b = call('PUT', base + f'/documents/{d}/content', {'content': text, 'baseVersion': g['data']['version']}, t); assert s == 200, b
    return d


notes = doc('Fotosintesis', '# Fotosíntesis\n\nLas plantas convierten luz en energía química en los cloroplastos.\n\n## Fase luminosa\n\nOcurre en los tilacoides y produce ATP.\n\n## Ciclo de Calvin\n\nFija CO2 en el estroma.\n')
ref = doc('Libro', '# Fotosíntesis\n\nIncluye fase luminosa, ciclo de Calvin, factores limitantes y fotorrespiración.')
big_text = '\n\n'.join(f'## Seccion {i}\n\n' + ('La mitocondria produce ATP en la respiración celular número %d. ' % i) * 25 for i in range(120))
big = doc('Enorme', big_text)
print('big doc chars:', len(big_text))

s, b = call('GET', AI + '/status?workspaceId=' + w, None, t); ok('status', s, 200); print('  ', b['data'])
ok('status enabled', b['data']['enabled'], True)

# ---- A/B regression
s, ev = sse(AI + '/notes', {'workspaceId': w, 'topic': 'Mitosis', 'length': 'SHORT'}, t); ok('notes stream', s, 200)
names = [e[0] for e in ev]; ok('notes events', names[0] == 'meta' and names[-1] == 'done' and 'delta' in names, True)
s, ev = sse(AI + '/rewrite', {'text': 'hola mundo', 'action': 'FORMAL'}, t); ok('rewrite', s, 200)
s, b = call('POST', AI + '/deck', {'workspaceId': w, 'mode': 'FLASHCARDS', 'sources': [{'artifactId': notes}]}, t); ok('deck', s, 200)
s, b = call('POST', AI + '/quiz', {'workspaceId': w, 'topic': 'x', 'count': 6}, t); ok('quiz', s, 200)

# ---- C
s, b = call('POST', AI + '/perspectives', {'workspaceId': w, 'sources': [{'artifactId': notes}], 'lenses': ['CRITICAL', 'HISTORICAL'], 'customLenses': ['un agricultor']}, t); ok('perspectives', s, 200)
if s == 200: ok('perspectives markdown', b['data']['markdown'].startswith('# '), True); print('  ', b['data']['markdown'][:200].replace('\n', ' | '))
s, b = call('POST', AI + '/perspectives', {'workspaceId': w}, t); ok('perspectives needs material', s, 400)
s, b = call('POST', AI + '/debate', {'workspaceId': w, 'motion': 'Los transgénicos son seguros'}, t); ok('debate', s, 200)
if s == 200: ok('debate has both sides', len(b['data']['pro']['arguments']) > 0 and len(b['data']['con']['arguments']) > 0, True)
s, b = call('POST', AI + '/debate', {'workspaceId': w, 'motion': ''}, t); ok('debate blank motion', s, 400)
s, b = call('POST', AI + '/gaps', {'workspaceId': w, 'notes': [{'artifactId': notes}], 'reference': [{'artifactId': ref}]}, t); ok('gaps', s, 200)
if s == 200: ok('gaps sorted high first', b['data']['gaps'][0]['severity'], 'HIGH'); print('  coverage', b['data']['coverage'])
s, b = call('POST', AI + '/gaps', {'workspaceId': w, 'notes': [{'artifactId': notes}], 'reference': [{'artifactId': notes}]}, t); ok('gaps overlap rejected', s, 400)
s, b = call('POST', AI + '/gaps', {'workspaceId': w, 'notes': []}, t); ok('gaps needs notes', s, 400)
s, b = call('POST', AI + '/gaps', {'workspaceId': w, 'notes': [{'artifactId': notes}]}, other); ok('gaps foreign workspace', s in (403, 404), True)

# ---- D conversations
s, b = call('POST', AI + '/conversations', {'workspaceId': w, 'mode': 'DOCUMENTS', 'sources': [{'artifactId': notes}]}, t); ok('create conversation', s, 201)
conv = b['data']['conversation']['id']; ok('title from doc', b['data']['conversation']['title'], 'Fotosintesis')
s, ev = sse(f'{AI}/conversations/{conv}/messages', {'content': 'Qué es la fase luminosa?'}, t); ok('send message', s, 200)
done = [e for e in ev if e[0] == 'done'][0][1]; meta = ev[0][1]
ok('meta conversation', meta.get('conversationId'), conv)
ok('citation to doc', [c['artifactId'] for c in done['citations']], [notes])
s, b = call('GET', f'{AI}/conversations/{conv}', None, t); ok('get conversation', s, 200)
ok('two messages saved', [m['role'] for m in b['data']['messages']], ['USER', 'ASSISTANT'])
ok('assistant citations kept', len(b['data']['messages'][1]['citations']), 1)
s, b = call('GET', f'{AI}/conversations/{conv}', None, other); ok('other user cannot open', s, 404)
s, ev = sse(f'{AI}/conversations/{conv}/messages', {'content': 'hola'}, other); ok('other user cannot write', s, 404)
s, b = call('GET', f'{AI}/conversations?workspaceId={w}', None, t); ok('list', len(b['data']), 1)
s, b = call('PATCH', f'{AI}/conversations/{conv}', {'title': 'Fotosíntesis 101'}, t); ok('rename', b['data']['title'], 'Fotosíntesis 101')
s, b = call('PATCH', f'{AI}/conversations/{conv}', {'sources': [{'artifactId': notes}, {'artifactId': ref}]}, t); ok('two sources', len(b['data']['sources']), 2)
s, ev = sse(f'{AI}/conversations/{conv}/messages', {'content': 'Compará ambos'}, t); ok('second message', s, 200)

# retrieval mode (document longer than the full-context limit)
s, b = call('POST', AI + '/conversations', {'workspaceId': w, 'mode': 'DOCUMENTS', 'sources': [{'artifactId': big}]}, t); ok('create big', s, 201)
bc = b['data']['conversation']['id']
s, ev = sse(f'{AI}/conversations/{bc}/messages', {'content': 'Qué produce la mitocondria?'}, t); ok('retrieval message', s, 200)
print('   events:', [e[0] for e in ev][:3], '...', ev[-1])
n = subprocess.run(['docker', 'exec', 'sophia-postgres', 'psql', '-U', 'sophia', '-d', 'sophia_ai', '-tAc', f"select chunk_count from ai_doc_index where artifact_id='{big}'"], capture_output=True, text=True).stdout.strip()
print('   indexed chunks:', n); ok('indexed', int(n or 0) > 10, True)
s, ev = sse(f'{AI}/conversations/{bc}/messages', {'content': 'y la sección 5?'}, t); ok('follow-up retrieval', s, 200)
def indexed_at():
    return subprocess.run(['docker', 'exec', 'sophia-postgres', 'psql', '-U', 'sophia', '-d', 'sophia_ai', '-tAc', f"select indexed_at from ai_doc_index where artifact_id='{big}'"], capture_output=True, text=True).stdout.strip()
at1 = indexed_at()
s, ev = sse(f'{AI}/conversations/{bc}/messages', {'content': 'otra pregunta sobre respiración'}, t)
ok('not re-indexed while the text is unchanged', at1 == indexed_at() and at1 != '', True)

# socratic + debate
s, b = call('POST', AI + '/conversations', {'workspaceId': w, 'mode': 'SOCRATIC', 'topic': 'Fotosíntesis'}, t); ok('socratic create', s, 201)
sc = b['data']['conversation']['id']
s, ev = sse(f'{AI}/conversations/{sc}/messages', {'content': 'Quiero aprender'}, t); ok('socratic message', s, 200)
s, ev = sse(f'{AI}/conversations/{sc}/messages', {'content': 'Terminemos', 'finish': True}, t); ok('socratic finish', s, 200)
s, b = call('POST', AI + '/conversations', {'workspaceId': w, 'mode': 'DEBATE', 'topic': 'Hay que usar IA'}, t); ok('debate needs stance', s, 400)
s, b = call('POST', AI + '/conversations', {'workspaceId': w, 'mode': 'DEBATE', 'topic': 'Hay que usar IA', 'stance': 'PRO'}, t); ok('debate create', s, 201)
dc = b['data']['conversation']['id']
s, ev = sse(f'{AI}/conversations/{dc}/messages', {'content': 'Creo que sí porque ahorra tiempo'}, t); ok('debate message', s, 200)
s, b = call('DELETE', f'{AI}/conversations/{dc}', None, t); ok('delete', s, 204)
s, b = call('GET', f'{AI}/conversations/{dc}', None, t); ok('deleted gone', s, 404)
s, b = call('POST', AI + '/conversations', {'workspaceId': w, 'mode': 'DOCUMENTS', 'sources': [{'artifactId': notes}]}, other); ok('foreign doc rejected', s in (403, 404), True)
s, b = call('POST', f'{AI}/conversations/{conv}/messages', {'content': ''}, t); ok('blank message', s, 400)


# ---- E: conversions
PNG = bytes([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]) + b'\x00' * 64
JPEG_BIG = bytes([0xFF, 0xD8, 0xFF]) + b'\x00' * (5 * 1024 * 1024 + 10)
s, ev = multipart(AI + '/ocr', {'workspaceId': w, 'mode': 'NOTES'}, ('page.png', PNG), t); ok('ocr streams', s, 200)
if s == 200: ok('ocr events', ev[0][0] == 'meta' and ev[-1][0] == 'done', True)
s, b = multipart(AI + '/ocr', {}, ('evil.png', b'<html><script>alert(1)</script>'), t); ok('ocr rejects a disguised file', s, 415)
s, b = multipart(AI + '/ocr', {}, ('big.jpg', JPEG_BIG), t); ok('ocr rejects an oversized image', s, 413)
s, b = multipart(AI + '/ocr', {'language': 'xx-123'}, ('page.png', PNG), t); ok('ocr validates the language', s, 400)
s, b = call('POST', AI + '/ocr', {}, t); ok('ocr needs a form', s in (400, 415), True)

s, b = call('POST', AI + '/tables', {'workspaceId': w, 'text': 'a;b\n1;2'}, t); ok('table from text', s, 200)
if s == 200: ok('table is rectangular', all(len(r['cells']) == len(b['data']['headers']) for r in b['data']['rows']), True)
s, b = multipart(AI + '/tables/file', {'workspaceId': w}, ('t.png', PNG), t); ok('table from a file', s, 200)
s, b = call('POST', AI + '/tables', {'workspaceId': w}, t); ok('table needs material', s, 400)
for kind in ('FLOWCHART', 'TIMELINE', 'CYCLE'):
    s, b = call('POST', AI + '/diagrams', {'workspaceId': w, 'kind': kind, 'topic': 'Proceso'}, t); ok('diagram ' + kind, s, 200)
    if s == 200:
        ids = {n['id'] for n in b['data']['nodes']}
        ok('diagram edges point at nodes ' + kind, all(e['from'] in ids and e['to'] in ids for e in b['data']['edges']), True)
s, b = call('POST', AI + '/diagrams', {'workspaceId': w, 'kind': 'FLOWCHART'}, t); ok('diagram needs material', s, 400)
s, b = call('POST', AI + '/diagrams', {'workspaceId': w, 'kind': 'PIE', 'topic': 'x'}, t); ok('diagram unknown kind', s, 400)
s, b = call('POST', AI + '/tags', {'workspaceId': w, 'text': '# Mitosis\nLa celula se divide'}, t); ok('tags', s, 200)
if s == 200: ok('tags are editor-ready', all(tag == tag.lower() and ' ' not in tag and not tag.startswith('#') for tag in b['data']['tags']), True)
s, b = call('POST', AI + '/tags', {'text': ''}, t); ok('tags need text', s, 400)
s, ev = sse(AI + '/listen', {'workspaceId': w, 'text': '# Titulo\n- uno\n- dos'}, t); ok('listen streams', s, 200)

# ---- security and hygiene
s, b = call('GET', AI + '/status'); ok('anonymous gets 401', s, 401)
s, b = call('POST', AI + '/tags', {'text': 'x'}, 'not-a-token'); ok('bad token gets 401', s, 401)
s, b = call('GET', 'http://localhost:8083/api/v1/admin/ai/settings', None, t); ok('plain user cannot read admin settings', s, 403)
s, b = call('PUT', 'http://localhost:8083/api/v1/admin/ai/settings', {'enabled': False, 'dailyTokensPerUser': 0, 'dailyTokensPerWorkspace': 0}, t); ok('plain user cannot change settings', s, 403)
s, b = call('GET', AI + '/conversations/not-a-uuid', None, t); ok('bad id is a 400', s, 400)
s, b = call('DELETE', AI + '/conversations', None, t); ok('delete all conversations', s, 200)
s, b = call('GET', f'{AI}/conversations?workspaceId={w}', None, t); ok('nothing left', len(b['data']), 0)
s, b = call('GET', 'http://localhost:8083/actuator/health/liveness'); ok('liveness is public', s, 200)
s, b = call('GET', 'http://localhost:8083/actuator/prometheus'); ok('metrics need credentials', s, 401)

# usage was logged
rows = subprocess.run(['docker', 'exec', 'sophia-postgres', 'psql', '-U', 'sophia', '-d', 'sophia_ai', '-tAc', "select feature||':'||count(*) from ai_generations group by feature order by 1"], capture_output=True, text=True).stdout.split()
print('   usage:', rows)
print('\nFAILS:', fails)
