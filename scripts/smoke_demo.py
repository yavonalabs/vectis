"""Read-only HTTP smoke checks for the disposable Vectis sample deployment.

Usage: python scripts/smoke_demo.py https://<demo-host>
Uses only the public sample accounts. Never point this at a customer installation.
Browser/mobile/accessibility checks remain separate release gates.
"""
import argparse
import json
import http.cookiejar
import urllib.error
import urllib.parse
import urllib.request
from html.parser import HTMLParser


class Inputs(HTMLParser):
    def __init__(self):
        super().__init__()
        self.values = {}

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'input' and attrs.get('name'):
            self.values[attrs['name']] = attrs.get('value', '')


def session():
    return urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def read(client, url, data=None):
    try:
        response = client.open(url, data=data, timeout=90)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.code, response.url, response.read().decode('utf-8'), response.headers


def require(condition, message):
    if not condition:
        raise RuntimeError(message)
    print('PASS:', message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('url', help='Base URL of the disposable sample demo')
    args = parser.parse_args()
    base = args.url.rstrip('/')
    target = urllib.parse.urlsplit(base)
    if target.scheme not in ('http', 'https') or not target.netloc or target.username or target.password:
        parser.error('Provide an HTTP(S) base URL without embedded credentials.')
    if target.path or target.query or target.fragment:
        parser.error('Use the host base URL, without a path, query or fragment.')
    anonymous = session()
    status, url, _, _ = read(anonymous, base + '/')
    require(status == 200 and urllib.parse.urlsplit(url).path == '/login', 'Root URL leads to sign-in')
    status, url, body, _ = read(anonymous, base + '/admin')
    require(status == 200 and urllib.parse.urlsplit(url).path == '/login', 'Anonymous workspace access requires sign-in')
    require('This demo is shared with other visitors.' in body, 'Public-demo notice is rendered')
    for asset in ('vectis-mark.svg', 'vectis-mark-light.svg', 'vectis-favicon.svg'):
        status, _, _, headers = read(anonymous, base + '/vectis-assets/' + asset)
        require(status == 200 and 'image/svg+xml' in headers.get('Content-Type', ''), 'Logo asset loads: ' + asset)
    status, _, _, _ = read(anonymous, base + '/h2-console/')
    require(status == 404, 'H2 console is disabled')
    for username, password in (('admin', 'admin'), ('user', 'password'), ('restricted', 'password')):
        client = session()
        status, _, html, _ = read(client, base + '/login')
        inputs = Inputs()
        inputs.feed(html)
        require(status == 200 and bool(inputs.values.get('_csrf')), username + ': sign-in includes CSRF token')
        payload = urllib.parse.urlencode({'username': username, 'password': password, '_csrf': inputs.values['_csrf']}).encode()
        status, url, _, _ = read(client, base + '/login', payload)
        require(status == 200 and urllib.parse.urlsplit(url).path == '/admin', username + ': sign-in reaches workspace')
        status, _, html, _ = read(client, base + '/admin/employee')
        require(status == 200 and 'Team Members' in html, username + ': sample record list renders')
        inputs = Inputs()
        inputs.feed(html)
        preview_url = base + '/admin/api/employee/action/toggleLeaveStatus/1/preview'
        payload = urllib.parse.urlencode({'_csrf': inputs.values['_csrf']}).encode()
        status, _, preview_body, _ = read(client, preview_url, payload)
        if username == 'admin':
            require(status == 200, 'Admin can preview a sample action using the rendered CSRF token')
            preview = json.loads(preview_body)
            require(preview.get('riskLevel') == 'MODERATE' and bool(preview.get('plainTextChanges')),
                    'Preview includes proposed changes and risk level')
        else:
            require(status == 403, 'Read-only account cannot preview actions even with valid CSRF')
        status, _, _, _ = read(client, preview_url, b'')
        require(status == 403, username + ': preview rejects missing CSRF')
        if username != 'admin':
            status, _, _, _ = read(client, base + '/admin/employee/edit/1')
            require(status == 403, 'Read-only account cannot open edit form')
            status, _, html, _ = read(client, base + '/admin/employee/view/1')
            require(status == 200 and 'Activity history is unavailable for your account.' in html,
                    'Restricted activity is unavailable, not falsely empty')
        if username == 'restricted':
            require('Related records unavailable.' in html and 'Engineering' not in html,
                    'Restricted relationships are unavailable without leaking department labels')
            for path in ('/admin/department', '/admin/department/view/1', '/admin/skill',
                         '/admin/employee/view/1/related/skills'):
                status, _, _, _ = read(client, base + path)
                require(status == 403, 'Restricted account denied: ' + path)
            status, _, results, _ = read(client, base + '/admin/api/search?q=Engineering')
            require(status == 200 and json.loads(results) == [], 'Restricted search excludes departments')
        status, _, html, _ = read(client, base + '/admin')
        inputs = Inputs()
        inputs.feed(html)
        require('Log out' in html and bool(inputs.values.get('_csrf')), username + ': logout control includes CSRF protection')
        status, url, _, _ = read(client, base + '/logout', urllib.parse.urlencode({'_csrf': inputs.values['_csrf']}).encode())
        require(status == 200 and urllib.parse.urlsplit(url).path == '/login', username + ': logout returns to sign-in')
        status, url, _, _ = read(client, base + '/admin')
        require(status == 200 and urllib.parse.urlsplit(url).path == '/login', username + ': workspace requires sign-in after logout')
    print('HTTP smoke checks passed. Browser, mobile, accessibility and mutation checks are still required.')


if __name__ == '__main__':
    main()
