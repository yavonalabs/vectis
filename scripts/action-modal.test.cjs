// Run with: node --test scripts/action-modal.test.cjs
// Exercise the actual browser controller's response handling without a DOM library.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const source = readFileSync('vectis-core/src/main/resources/static/vectis-assets/vectis.js', 'utf8');

test('confirmation form preserves initial version zero and clears absent versions', () => {
    const layout = readFileSync('vectis-core/src/main/resources/templates/vectis/layout.html', 'utf8');
    const binding = layout.match(/name="_version"\s+:value="([^"]+)"/)[1];
    assert.equal(vm.runInNewContext(binding, { previewData: { version: 0 } }), 0);
    assert.equal(vm.runInNewContext(binding, { previewData: { version: 4 } }), 4);
    assert.equal(vm.runInNewContext(binding, { previewData: null }), '');
});

function modalFor(response) {
    const context = vm.createContext({
        document: { addEventListener() {}, body: { dataset: { adminBase: '/portal/ops' } },
            getElementById() { return { focus() {} }; } },
        FormData: class {}, fetch: async () => response
    });
    vm.runInContext(source, context);
    const modal = vm.runInContext('actionModal()', context);
    modal.$el = { classList: { remove() {} } };
    modal.$nextTick = fn => fn();
    return modal;
}

test('delete dialog replaces the reviewed version between records, including zero', () => {
    const clicks = [];
    const nodes = {};
    const context = vm.createContext({ window: { crypto: { randomUUID: () => 'test-operation-key' } }, document: {
        body: { dataset: { adminBase: '/admin' } },
        addEventListener(name, fn) { if (name === 'click') clicks.push(fn); },
        getElementById(id) { return nodes[id] ||= { value: '', classList: { remove() {} } }; },
        querySelector() { return { focus() {} }; }
    } });
    vm.runInContext(source, context);
    for (const version of ['7', '0', undefined]) {
        const button = { dataset: { version }, getAttribute: name => name === 'data-slug' ? 'employee' : '1' };
        clicks[0]({ target: { closest: selector => selector === '[data-modal-type="delete"]' ? button : null } });
        assert.equal(nodes['delete-version'].value, version ?? '');
    }
});
const detail = { slug: 'employee', actionId: 'toggleLeaveStatus', entityId: '1' };
function response(status, body, type = 'application/json', redirected = false) {
    return { status, ok: status === 200, redirected, headers: { get: () => type }, json: async () => body };
}

test('valid preview requires reason before moderate action can submit', async () => {
    const modal = modalFor(response(200, { riskLevel: 'MODERATE', plainTextChanges: ['Status changed'] }));
    await modal.openAction(detail);
    assert.equal(modal.previewError, '');
    assert.equal(modal.canSubmit, false);
    modal.reasonProvided = 'Support ticket';
    assert.equal(modal.canSubmit, true);
});

for (const [name, reply, message] of [
    ['unauthenticated', response(401), /session may have expired/],
    ['login redirect', response(200, null, 'text/html', true), /sign in again/],
    ['denied', response(403, { error: 'Forbidden' }), /Preview access was denied/],
    ['HTML error', response(502, null, 'text/html'), /did not return a preview/],
    ['validation', response(422, { error: 'Invalid proposed state' }), /Invalid proposed state/]
]) {
    test(name + ' clears prior preview and keeps confirmation disabled', async () => {
        const modal = modalFor(reply);
        modal.previewData = { riskLevel: 'LOW' };
        await modal.openAction(detail);
        assert.match(modal.previewError, message);
        assert.equal(modal.previewData, null);
        assert.equal(modal.loadingPreview, false);
        assert.equal(modal.canSubmit, false);
    });
}
