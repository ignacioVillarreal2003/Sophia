"""End-to-end check of inviting people to a workspace and of accepting or declining the invitation.

Needs the stack running (authentication and document-workspace). Registers throw-away users, so run it only
against a development stack:   py api/document-workspace/e2e/e2e_invitations.py
"""
import json, subprocess, urllib.error, urllib.request, uuid

AUTH = 'http://localhost:8080/api/v1'
WS = 'http://localhost:8081/api/v1'


def call(method, url, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={'Content-Type': 'application/json'})
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {'raw': raw[:200]}


fails = 0


def ok(label, got, want):
    global fails
    good = got == want
    fails += 0 if good else 1
    print('OK  ' if good else 'FAIL', label, '' if good else f'got {got!r} wanted {want!r}')


def register(prefix):
    email = f'{prefix}-{uuid.uuid4().hex[:6]}@t.com'
    pw = 'Passw0rd!x1'
    call('POST', AUTH + '/authentication/register', {'firstName': prefix, 'lastName': 'T', 'email': email, 'password': pw})
    subprocess.run(['docker', 'exec', 'sophia-postgres', 'psql', '-U', 'sophia', '-d', 'sophia_authentication', '-c',
                    f"update users set email_verified=true where email='{email}'"], capture_output=True)
    return email, call('POST', AUTH + '/authentication/login', {'email': email, 'password': pw})[1]['data']['accessToken']


owner_email, owner = register('owner')
ana_email, ana = register('ana')
bob_email, bob = register('bob')
eve_email, eve = register('eve')

w = call('POST', WS + '/workspaces', {'name': 'Equipo'}, owner)[1]['data']['id']
base = f'{WS}/workspaces/{w}'
mine = WS + '/invitations'

# ---- inviting
s, b = call('POST', base + '/invitations', {'email': ana_email, 'role': 'EDITOR'}, owner)
ok('an admin can invite', s, 201)
inv_ana = b['data']['id']
ok('the invitation is pending', b['data']['status'], 'PENDING')
ok('inviting does not make a member', call('GET', base, None, ana)[0] in (403, 404), True)
ok('the invited person sees it', [i['id'] for i in call('GET', mine, None, ana)[1]['data']['content']], [inv_ana])
ok('and nobody else does', call('GET', mine, None, eve)[1]['data']['content'], [])
ok('inviting twice conflicts', call('POST', base + '/invitations', {'email': ana_email, 'role': 'VIEWER'}, owner)[0], 409)
ok('a non-admin cannot invite', call('POST', base + '/invitations', {'email': eve_email, 'role': 'VIEWER'}, eve)[0] in (403, 404), True)
ok('an unknown email is 404', call('POST', base + '/invitations', {'email': 'nobody-here@t.com', 'role': 'VIEWER'}, owner)[0], 404)
ok('the owner is already a member', call('POST', base + '/invitations', {'email': owner_email, 'role': 'VIEWER'}, owner)[0], 409)
ok('admins see the pending list', [i['email'] for i in call('GET', base + '/invitations', None, owner)[1]['data']], [ana_email])
ok('others cannot read that list', call('GET', base + '/invitations', None, eve)[0] in (403, 404), True)

# ---- paging, search and filters
zed_email, zed = register('zed')
for n in range(25):
    e, t_ = register(f'ws{n}')
    ww = call('POST', WS + '/workspaces', {'name': f'Proyecto {chr(97 + n % 26)}{n}'}, t_)[1]['data']['id']
    call('POST', f'{WS}/workspaces/{ww}/invitations', {'email': zed_email, 'role': ['VIEWER', 'EDITOR', 'ADMIN'][n % 3]}, t_)
page0 = call('GET', mine + '?size=10', None, zed)[1]['data']
ok('a page holds at most the size', len(page0['content']), 10)
ok('and says how many there are', (page0['totalElements'], page0['totalPages'], page0['last']), (25, 3, False))
ok('the last page has the rest', len(call('GET', mine + '?size=10&page=2', None, zed)[1]['data']['content']), 5)
ok('search by workspace name', len(call('GET', mine + '?q=proyecto%20a', None, zed)[1]['data']['content']), 1)
ok('search is not a pattern', call('GET', mine + '?q=%25', None, zed)[1]['data']['content'], [])
ok('filter by role', len(call('GET', mine + '?role=ADMIN&size=50', None, zed)[1]['data']['content']), 8)
ok('sorted by name', [i['workspaceName'] for i in call('GET', mine + '?sort=NAME&desc=false&size=3', None, zed)[1]['data']['content']], ['Proyecto a0', 'Proyecto b1', 'Proyecto c2'])
ok('inviters to pick', len(call('GET', mine + '/inviters', None, zed)[1]['data']), 20)
ok('inviters are narrowed by the text', len(call('GET', mine + '/inviters?q=ws3', None, zed)[1]['data']), 1)
ok('a date range that ended before', call('GET', mine + '?to=2000-01-01', None, zed)[1]['data']['content'], [])
ok('filter by who invited', len(call('GET', mine + '?invitedBy=' + owner_email.upper(), None, zed)[1]['data']['content']), 0)

# ---- answering
ok('someone else cannot accept it', call('POST', f'{mine}/{inv_ana}/accept', None, eve)[0], 404)
s, b = call('POST', f'{mine}/{inv_ana}/accept', None, ana)
ok('the invited person accepts', s, 200)
ok('it is accepted', b['data']['status'], 'ACCEPTED')
ok('now a member', call('GET', base, None, ana)[0], 200)
members = call('GET', base + '/members', None, owner)[1]['data']['content']
ok('as an editor', [m['role'] for m in members if m['email'] == ana_email], ['EDITOR'])
ok('no longer pending', call('GET', mine, None, ana)[1]['data']['content'], [])
ok('cannot accept twice', call('POST', f'{mine}/{inv_ana}/accept', None, ana)[0], 409)

# ---- declining and withdrawing
inv_bob = call('POST', base + '/invitations', {'email': bob_email, 'role': 'VIEWER'}, owner)[1]['data']['id']
ok('decline works', call('POST', f'{mine}/{inv_bob}/decline', None, bob)[0], 200)
ok('declined means not a member', call('GET', base, None, bob)[0] in (403, 404), True)
ok('cannot accept after declining', call('POST', f'{mine}/{inv_bob}/accept', None, bob)[0], 409)
inv_bob2 = call('POST', base + '/invitations', {'email': bob_email, 'role': 'VIEWER'}, owner)[1]['data']['id']
ok('can invite again after a decline', bool(inv_bob2), True)
ok('an admin withdraws it', call('DELETE', f'{base}/invitations/{inv_bob2}', None, owner)[0], 200)
ok('it disappears for the invited person', call('GET', mine, None, bob)[1]['data']['content'], [])
ok('cannot accept a withdrawn one', call('POST', f'{mine}/{inv_bob2}/accept', None, bob)[0], 409)

# ---- expiry
inv_eve = call('POST', base + '/invitations', {'email': eve_email, 'role': 'VIEWER'}, owner)[1]['data']['id']
subprocess.run(['docker', 'exec', 'sophia-postgres', 'psql', '-U', 'sophia', '-d', 'sophia_document_workspace', '-c',
                f"update workspace_invitations set expires_at = now() - interval '1 minute' where id='{inv_eve}'"],
               capture_output=True)
ok('an expired invitation is not listed', call('GET', mine, None, eve)[1]['data']['content'], [])
ok('and cannot be accepted', call('POST', f'{mine}/{inv_eve}/accept', None, eve)[0], 400)
ok('an expired one can be replaced', call('POST', base + '/invitations', {'email': eve_email, 'role': 'VIEWER'}, owner)[0], 201)

print('\nFAILURES:', fails)
raise SystemExit(1 if fails else 0)
