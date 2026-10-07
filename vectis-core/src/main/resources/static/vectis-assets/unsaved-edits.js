// Form state remains in memory only; no record values are stored in browser storage.
function createUnsavedEditState(snapshot) {
    let baseline = null;
    let forced = false;
    let saved = false;
    return {
        attach(form, validationReturn) {
            if (!validationReturn || baseline === null) {
                baseline = snapshot(form);
                forced = Boolean(validationReturn);
            }
            saved = false;
        },
        dirty(form) { return Boolean(form && !saved && (forced || snapshot(form) !== baseline)); },
        retain() { forced = true; saved = false; },
        saved() { saved = true; forced = false; }
    };
}

(function installUnsavedEditGuard() {
    let form = null;
    let nativeSubmission = false;
    let submittedSnapshot = null;
    let requiresReload = false;
    const snapshot = element => JSON.stringify(Array.from(element.elements)
        .filter(field => field.name && !field.disabled && !['hidden', 'submit', 'button', 'reset'].includes(field.type))
        .map(field => [field.name, field.type === 'checkbox' || field.type === 'radio' ? field.checked
            : field.multiple ? Array.from(field.selectedOptions).map(option => option.value) : field.value]));
    const state = createUnsavedEditState(snapshot);
    function attach() {
        const next = document.getElementById('entity-form');
        if (next === form) return;
        form = next;
        submittedSnapshot = null;
        requiresReload = false;
        if (form) state.attach(form, form.dataset.unsaved === 'true');
    }
    function dirty() { return form?.isConnected && state.dirty(form); }
    function showMessage(message) {
        const notice = document.getElementById('unsaved-session-status');
        if (notice) {
            notice.hidden = false;
            notice.textContent = message;
            notice.focus();
        }
    }
    document.addEventListener('DOMContentLoaded', attach);
    document.addEventListener('htmx:afterSwap', attach);
    window.addEventListener('beforeunload', event => {
        if (dirty() && !nativeSubmission) { event.preventDefault(); event.returnValue = ''; }
    });
    document.addEventListener('submit', event => {
        if (event.target === form && (!window.htmx || !form.hasAttribute('hx-post'))) {
            nativeSubmission = true;
            setTimeout(() => { nativeSubmission = false; }, 0);
        }
    });
    document.addEventListener('htmx:beforeRequest', event => {
        const detail = event.detail;
        if (detail.elt === form) {
            if (requiresReload) { event.preventDefault(); return; }
            submittedSnapshot = snapshot(form);
        }
        if (dirty() && detail.elt !== form && detail.target?.contains(form)
                && !window.confirm('Leave this form and discard your unsaved changes?')) event.preventDefault();
    });
    document.addEventListener('htmx:beforeOnLoad', event => {
        const detail = event.detail;
        if (!form || detail.elt !== form) return;
        const xhr = detail.xhr;
        if (xhr.status === 401 && dirty()) {
            event.preventDefault();
            showMessage('Your session has ended. These edits are not saved. Copy anything you need before signing in again.');
        } else if (xhr.status >= 200 && xhr.status < 300 && xhr.getResponseHeader('HX-Redirect')) {
            if (submittedSnapshot !== null && snapshot(form) !== submittedSnapshot) {
                event.preventDefault();
                requiresReload = true;
                state.retain();
                showMessage('The submitted values were saved, but you changed this form while saving. Your newer edits are still here and are not saved. Copy them, then reload the record before editing again.');
            } else {
                state.saved();
            }
        } else if (submittedSnapshot !== null && snapshot(form) !== submittedSnapshot) {
            event.preventDefault();
            showMessage('The response applies to earlier values. Your newer edits have been kept. Review them and try saving again.');
        }
    });
})();
