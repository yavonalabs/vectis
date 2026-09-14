function adminBase() { return document.body.dataset.adminBase; }

function recordFilterRow() {
    return {
        kind: 'text', choices: [], value: '', operator: 'eq',
        get comparable() { return ['number', 'date', 'datetime-local'].includes(this.kind); },
        get unary() { return ['empty', 'notEmpty'].includes(this.operator); },
        init() {
            this.readField();
            this.operator = this.$refs.operator.dataset.initial || 'eq';
            this.value = this.$el.dataset.initialValue || '';
        },
        readField() {
            const option = this.$refs.field.selectedOptions[0];
            this.kind = option?.dataset.kind || 'text';
            this.choices = this.kind === 'boolean' ? [{value:'true', label:'Yes'}, {value:'false', label:'No'}]
                : (option?.dataset.choices || '').split('|').filter(Boolean).map(value => ({value,
                    label: value.charAt(0) + value.slice(1).toLowerCase().replaceAll('_', ' ')}));
        },
        changeField() { this.readField(); this.value = ''; this.operator = 'eq'; },
        clear() { this.$refs.field.value = ''; this.changeField(); }
    };
}

function relationshipPicker() {
    return {
        search: '', page: 0, hasNext: false, searched: false, loading: false, error: '', sequence: 0, resultCount: 0,
        async load(page) {
            const sequence = ++this.sequence;
            this.loading = true; this.error = '';
            try {
                const url = new URL(this.$root.dataset.optionsUrl, window.location.origin);
                url.searchParams.set('search', this.search);
                url.searchParams.set('page', page);
                const response = await fetch(url);
                if (!response.ok) throw new Error('Options could not be loaded. Your selection has been kept. Try again.');
                const result = await response.json();
                if (sequence !== this.sequence) return;
                const select = this.$refs.selection;
                const current = select.selectedOptions[0];
                const selectedId = select.value;
                const options = [new Option('Not assigned', '')];
                if (selectedId && !result.items.some(item => item.id === selectedId))
                    options.push(new Option(current.textContent, selectedId));
                for (const item of result.items) options.push(new Option(item.label, item.id));
                select.replaceChildren(...options);
                select.value = selectedId;
                this.page = result.page; this.hasNext = result.hasNext; this.searched = true;
                this.resultCount = result.items.length;
            } catch (error) {
                if (sequence === this.sequence) this.error = error.message;
            } finally { if (sequence === this.sequence) this.loading = false; }
        }
    };
}

document.addEventListener('htmx:beforeSwap', event => {
    if (event.detail.xhr.status === 400 && event.detail.xhr.getResponseHeader('X-Vectis-Filter-Error') === 'true') {
        event.detail.shouldSwap = true;
        event.detail.isError = false;
    }
});
document.addEventListener('htmx:afterSwap', () => document.getElementById('filter-error')?.focus());

function globalSearch() {
    return {
        commandOpen: false, commandSearch: '', peekDrawerOpen: false,
        searchResults: [], isSearching: false, searchError: '',
        init() {
            let timer, sequence = 0;
            this.$watch('commandSearch', value => {
                clearTimeout(timer);
                const requestId = ++sequence;
                this.searchError = '';
                this.searchResults = [];
                this.isSearching = Boolean(value && value.trim().length >= 2);
                if (!this.isSearching) return;
                timer = setTimeout(async () => {
                    try {
                        const response = await fetch(adminBase() + '/api/search?q=' + encodeURIComponent(value));
                        if (!response.ok) throw new Error('Search is unavailable. Try again.');
                        const data = await response.json();
                        if (sequence === requestId) this.searchResults = data;
                    } catch (error) {
                        if (sequence === requestId) this.searchError = 'Search is unavailable. Try again.';
                    } finally {
                        if (sequence === requestId) this.isSearching = false;
                    }
                }, 300);
            });
        }
    };
}

function actionModal() {
    return {
        loadingPreview: false, previewData: null, previewError: '', submitting: false,
        reasonProvided: '', actionId: '', entityId: '', slug: '', requestSequence: 0,
        get canSubmit() {
            return !this.loadingPreview && !this.submitting && !!this.previewData && !this.previewError
                && (this.previewData.riskLevel === 'LOW' || !!this.reasonProvided.trim());
        },
        async openAction(detail) {
            const requestId = ++this.requestSequence;
            this.slug = detail.slug; this.actionId = detail.actionId; this.entityId = detail.entityId;
            this.reasonProvided = ''; this.previewData = null; this.previewError = ''; this.submitting = false;
            this.loadingPreview = true;
            this.$el.classList.remove('hidden');
            this.$nextTick(() => document.getElementById('action-cancel').focus());
            try {
                const form = document.getElementById('modal-action-form');
                const formData = new FormData(form);
                const url = adminBase() + '/api/' + encodeURIComponent(this.slug) + '/action/'
                    + encodeURIComponent(this.actionId) + '/' + encodeURIComponent(this.entityId) + '/preview';
                const response = await fetch(url, {method: 'POST', body: formData});
                const data = await response.json();
                if (!response.ok) throw new Error(data.error || 'Preview failed. Try again.');
                if (requestId === this.requestSequence) this.previewData = data;
            } catch (error) {
                if (requestId === this.requestSequence) this.previewError = error.message || 'Preview failed. Try again.';
            } finally {
                if (requestId === this.requestSequence) this.loadingPreview = false;
            }
        }
    };
}

        function showToast(message, type = 'success') {
            const container = document.getElementById('toast-container');
            const toast = document.createElement('div');
            
            let borderColor = 'border-[#10b981]/40';
            let iconColor = 'text-[#10b981]';
            let icon = '&check;';
            
            if (type === 'error') {
                borderColor = 'border-rose-500/40';
                iconColor = 'text-rose-400';
                icon = '&times;';
            } else if (type === 'info') {
                borderColor = 'border-blue-500/40';
                iconColor = 'text-blue-400';
                icon = '&#8505;';
            }

            toast.className = `toast ${borderColor}`;
            toast.innerHTML = `
                <div class="flex items-center gap-2.5 text-xs font-medium text-white">
                    <span class="${iconColor} font-bold text-sm leading-none">${icon}</span>
                    <span><span class="toast-message"></span></span>
                    <button onclick="this.parentElement.parentElement.remove()" class="ml-3 text-[#9ca3af] hover:text-white font-bold">&times;</button>
                </div>
            `;
            toast.querySelector('.toast-message').textContent = message;
            container.appendChild(toast);
            setTimeout(() => toast.classList.add('show'), 50);
            setTimeout(() => {
                toast.classList.remove('show');
                setTimeout(() => toast.remove(), 250);
            }, 3000);
        }

        document.addEventListener('click', function(e) {
            const deleteBtn = e.target.closest('[data-modal-type="delete"]');
            if (deleteBtn) {
                const slug = deleteBtn.getAttribute('data-slug');
                const encodedId = deleteBtn.getAttribute('data-id');
                window.vectisDeleteTrigger = deleteBtn;
                document.getElementById('delete-reason').value = '';
                document.getElementById('modal-record-id').innerText = deleteBtn.dataset.recordLabel || '#' + encodedId;
                document.getElementById('modal-delete-form').action = adminBase() + '/' + slug + '/delete/' + encodedId;
                document.getElementById('delete-modal').classList.remove('hidden');
                document.querySelector('#delete-modal button[type="button"]').focus();
                return;
            }

            const actionBtn = e.target.closest('[data-modal-type="action"]');
            if (actionBtn) {
                window.vectisActionTrigger = actionBtn;
                const slug = actionBtn.getAttribute('data-slug');
                const actionId = actionBtn.getAttribute('data-action-id');
                const label = actionBtn.getAttribute('data-label');
                const title = actionBtn.getAttribute('data-title');
                const desc = actionBtn.getAttribute('data-desc');
                const encodedId = actionBtn.getAttribute('data-id');

                document.getElementById('action-modal-title').innerText = title || label;
                document.getElementById('action-modal-desc').textContent = (actionBtn.dataset.recordLabel || 'Record #' + encodedId) + ': ' + (desc || '');
                document.getElementById('action-modal-btn').innerText = label;
                document.getElementById('modal-action-form').action = adminBase() + '/' + slug + '/action/' + actionId + '/' + encodedId;
                
                window.dispatchEvent(new CustomEvent('open-action-modal', {
                    detail: { slug: slug, actionId: actionId, entityId: encodedId }
                }));
                return;
            }
        });

        function closeDeleteModal() { document.getElementById('delete-modal').classList.add('hidden'); window.vectisDeleteTrigger?.focus(); }
        function closeActionModal() { document.getElementById('action-modal').classList.add('hidden'); if (window.vectisActionTrigger?.isConnected) window.vectisActionTrigger.focus(); }

        // Keep keyboard navigation within the active confirmation dialog.
        document.addEventListener('keydown', event => {
            const dialog = ['action-modal', 'delete-modal'].map(id => document.getElementById(id))
                .find(element => element && !element.classList.contains('hidden'));
            if (!dialog) return;
            if (event.key === 'Escape') {
                if (dialog.id === 'delete-modal') closeDeleteModal();
                else closeActionModal();
                return;
            }
            if (event.key !== 'Tab') return;
            const controls = [...dialog.querySelectorAll('button:not([disabled]), input:not([type="hidden"]):not([disabled]), a[href], select, textarea')]
                .filter(element => element.getClientRects().length > 0);
            if (!controls.length) return;
            const first = controls[0], last = controls[controls.length - 1];
            if (!dialog.contains(document.activeElement) || (event.shiftKey && document.activeElement === first)) {
                event.preventDefault(); (event.shiftKey ? last : first).focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault(); first.focus();
            }
        });

        function focusFormErrors() {
            document.getElementById('form-errors')?.focus();
        }
        document.addEventListener('DOMContentLoaded', focusFormErrors);
        document.addEventListener('htmx:afterSwap', focusFormErrors);


        // Row Click: Opens Side Drawer via HTMX
        document.addEventListener('click', function(e) {
            const row = e.target.closest('.table-row-item');
            if (row && !e.target.closest('.no-row-click, button, a, input, select')) {
                const slug = row.getAttribute('data-slug');
                const id = encodeURIComponent(row.getAttribute('data-id'));
                if (slug && id) {
                    // Trigger Alpine to open drawer
                    window.dispatchEvent(new CustomEvent('open-peek-drawer'));
                    // Update Drawer Header dynamically
                    document.getElementById('drawer-id').innerText = '#' + id;
                    document.getElementById('drawer-title').innerText = slug.toUpperCase();
                    document.getElementById('drawer-fullpage-link').href = adminBase() + '/' + slug + '/view/' + id;

                    // Show a loading spinner in the drawer content
                    document.getElementById('drawer-content-placeholder').innerHTML = '<div class="flex justify-center items-center h-48"><svg class="animate-spin h-6 w-6 text-[#10b981]" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path></svg></div>';
                    
                    // Fetch the content
                    htmx.ajax('GET', adminBase() + '/' + slug + '/peek/' + id, {target: '#drawer-content-placeholder', swap: 'innerHTML'});
                }
            }
        });

