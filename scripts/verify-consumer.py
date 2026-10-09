"""Install fixture: two independent JVMs, one persistent database, one exact retry.
Run after mvn install and mvn -f verification/consumer/pom.xml package.
For PostgreSQL set VECTIS_CONSUMER_DB and credentials; this script never deletes DB data.
"""
import base64, http.cookiejar, json, os, pathlib, subprocess, time, urllib.request, urllib.parse, uuid
from html.parser import HTMLParser

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE = 'http://localhost:18089/portal/ops'
JAR = ROOT / 'verification/consumer/target/vectis-consumer-1.0.jar'
AUTH = 'Basic ' + base64.b64encode(b'installer:disposable-fixture').decode()

class Tokens(HTMLParser):
    def __init__(self): super().__init__(); self.csrf = None; self.links = {}; self.anchor = None
    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == 'input' and values.get('name') == '_csrf': self.csrf = values.get('value')
        if tag == 'a': self.anchor = [values.get('href', ''), '']
    def handle_data(self, data):
        if self.anchor is not None: self.anchor[1] += data
    def handle_endtag(self, tag):
        if tag == 'a' and self.anchor is not None:
            self.links[self.anchor[1].strip()] = self.anchor[0]; self.anchor = None

def start(log, base=BASE, arguments=()):
    java = str(pathlib.Path(os.environ['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')) if os.environ.get('JAVA_HOME') else 'java'
    proc = subprocess.Popen([java, '-jar', str(JAR), '--server.port=18089', *arguments], cwd=ROOT / 'verification/consumer', stdout=log, stderr=subprocess.STDOUT,
                            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    client.addheaders = [('Authorization', AUTH)]
    for _ in range(90):
        if proc.poll() is not None: raise RuntimeError('Consumer exited; inspect consumer log')
        try:
            with client.open(base, timeout=3) as response: response.read()
            return proc, client
        except urllib.error.HTTPError as error:
            if error.code >= 500:
                stop(proc)
                raise RuntimeError('Consumer returned a server error; inspect consumer log') from error
            time.sleep(1)
        except Exception: time.sleep(1)
    proc.terminate(); proc.wait(timeout=20)
    raise RuntimeError('Consumer did not become ready')

def post(client, path, values):
    with client.open(BASE + '/consumer-record/view/1') as response: html = response.read().decode()
    tokens = Tokens(); tokens.feed(html)
    assert tokens.csrf, 'No CSRF token rendered'
    payload = dict(values, _csrf=tokens.csrf)
    with client.open(BASE + path, urllib.parse.urlencode(payload).encode()) as response: return response.read().decode()

def stop(proc):
    proc.terminate(); proc.wait(timeout=30)

def main():
    logpath = ROOT / 'target/consumer-verification.log'
    logpath.parent.mkdir(parents=True, exist_ok=True)
    with logpath.open('w', encoding='utf-8') as log:
        proc, client = start(log)
        try:
            preview = json.loads(post(client, '/api/consumer-record/action/complete/1/preview', {}))
            request = {'_operation': str(uuid.uuid4()), '_proposal': preview['proposal'], '_version': preview['version'], '_reason': 'Install restart verification ' + str(uuid.uuid4())}
            post(client, '/consumer-record/action/complete/1', request)
            with client.open(BASE + '/api/operations/' + request['_operation']) as response: first = json.load(response)
            assert first['outcome'] == 'COMMITTED'
            before_restart = json.loads(post(client, '/api/consumer-record/action/complete/1/preview', {}))
            with client.open('http://localhost:18089/portal/vectis-assets/vendor/alpine.min.js') as response: assert response.status == 200
            view_name = 'Restart view ' + str(uuid.uuid4())
            page = post(client, '/consumer-record/saved-views', {'name': view_name, 'state': 'search=Independent&size=25'})
            links = Tokens(); links.feed(page)
            view_path = links.links[view_name]
        finally: stop(proc)
        # Force schema validation on restart; no Hibernate update may repair a mismatch.
        os.environ['VECTIS_CONSUMER_DDL'] = 'validate'
        proc, client = start(log)
        try:
            post(client, '/consumer-record/action/complete/1', request)
            with client.open(BASE + '/api/operations/' + request['_operation']) as response: recovered = json.load(response)
            assert recovered == first
            after_restart = json.loads(post(client, '/api/consumer-record/action/complete/1/preview', {}))
            assert after_restart['version'] == before_restart['version'], 'Retry changed the record version'
            assert after_restart['plainTextChanges'] == before_restart['plainTextChanges'], 'Retry repeated the effect'
            with client.open(BASE + '/consumer-record/view/1') as response: detail = response.read().decode()
            assert detail.count(request['_reason']) == 1, 'Duplicate or missing success audit'
            with client.open('http://localhost:18089' + view_path) as response:
                assert response.geturl().endswith('/consumer-record?search=Independent&size=25'), 'Saved view state did not survive restart'
                assert view_name in response.read().decode(), 'Saved view not listed after restart'
            post(client, view_path.removeprefix('/portal/ops') + '/delete', {})
        finally: stop(proc)
        proc, client = start(log, 'http://localhost:18089/admin', ('--server.servlet.context-path=/', '--vectis.path=/admin'))
        try:
            with client.open('http://localhost:18089/admin/consumer-record/edit/1') as response:
                tokens = Tokens(); tokens.feed(response.read().decode()); assert tokens.csrf, 'Default-path edit form missing CSRF'
            with client.open('http://localhost:18089/vectis-assets/vendor/alpine.min.js') as response: assert response.status == 200
        finally: stop(proc)
    print('PASS: separately packaged consumer, default/custom paths, CSRF, managed mutation, persistent restart, exact replay, single audit, personal view restart/cleanup, schema validation, local asset')

if __name__ == '__main__': main()
