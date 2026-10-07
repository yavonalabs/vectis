"""Create, edit and remove one unique fictional record on the disposable public demo.

Run explicitly with --allow-mutations. Never use against a customer database.
"""
import argparse
import re
import uuid
import urllib.parse
from smoke_demo import Inputs, read, require, session


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--allow-mutations', action='store_true')
    args = parser.parse_args()
    if not args.allow_mutations:
        parser.error('Explicit --allow-mutations is required for the disposable demo.')
    base = 'https://demo.vectis.yavonalabs.com'
    client = session()

    def fields(path):
        status, _, html, _ = read(client, base + path)
        require(status == 200, 'Form loads: ' + path)
        inputs = Inputs('entity-form' if '/employee/' in path else None)
        inputs.feed(html)
        require(bool(inputs.values.get('_csrf')), 'Rendered CSRF token is present')
        return inputs.values

    def post(path, payload):
        return read(client, base + path, urllib.parse.urlencode(payload).encode())

    login = fields('/login')
    status, url, _, _ = post('/login', dict(login, username='admin', password='admin'))
    require(status == 200 and urllib.parse.urlsplit(url).path == '/admin', 'Admin authenticated')
    email = 'release-check-' + uuid.uuid4().hex + '@example.com'
    record_id = None
    print('Disposable fixture email:', email, flush=True)
    try:
        create = fields('/admin/employee/create')
        status, _, _, _ = post('/admin/employee/save', dict(create, firstName='Release', lastName='Verification',
                email=email, salary='60000', status='ACTIVE', _reason='Hosted create verification'))
        require(status == 200, 'Create request completed')
        status, _, html, _ = read(client, base + '/admin/employee?search=' + urllib.parse.quote(email))
        ids = set(re.findall(r'/admin/employee/view/(\d+)', html))
        require(status == 200 and email in html and len(ids) == 1, 'Created record is independently readable')
        record_id = ids.pop()
        edit = fields('/admin/employee/edit/' + record_id)
        status, _, _, _ = post('/admin/employee/save', dict(edit, firstName='Verified', _reason='Hosted edit verification'))
        require(status == 200, 'Edit request completed')
        status, _, html, _ = read(client, base + '/admin/employee/view/' + record_id)
        require(status == 200 and 'Verified' in html and 'Hosted create verification' in html
                and 'Hosted edit verification' in html, 'Updated record and both audit reasons render')
    finally:
        if record_id:
            cleanup = fields('/admin/employee/edit/' + record_id)
            status, _, _, _ = post('/admin/employee/delete/' + record_id,
                    {'_csrf': cleanup['_csrf'], '_version': cleanup.get('version', ''), '_operation': str(uuid.uuid4()),
                     '_reason': 'Hosted disposable fixture cleanup'})
            require(status == 200, 'Delete request completed')
            status, _, _, _ = read(client, base + '/admin/employee/view/' + record_id)
            require(status == 404, 'Disposable record was removed')
        else:
            print('No record ID resolved. If creation succeeded, inspect the unique email above before retrying.')


if __name__ == '__main__':
    main()
