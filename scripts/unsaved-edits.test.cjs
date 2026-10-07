const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const {readFileSync} = require('node:fs');
const source = readFileSync('vectis-core/src/main/resources/static/vectis-assets/unsaved-edits.js', 'utf8');
function harness() {
    const doc = {}, win = {};
    let form = {elements:[{name:'firstName', type:'text', value:'Alice'}], dataset:{}, isConnected:true,
        hasAttribute: () => true};
    const notice = {hidden:true, focus(){}};
    const context = vm.createContext({document:{addEventListener:(n,f)=>doc[n]=f,
        getElementById:id=>id==='entity-form'?form:notice}, window:{htmx:{}, addEventListener:(n,f)=>win[n]=f, confirm:()=>false}, setTimeout});
    vm.runInContext(source, context);
    doc.DOMContentLoaded();
    function fire(name, detail, target) {
        let prevented=false;
        (doc[name] || win[name])({detail,target,preventDefault(){prevented=true;}});
        return prevented;
    }
    return {fire, form, notice, swap(next){form=next; doc['htmx:afterSwap']();}};
}
test('unchanged and reverted forms do not warn; changed values do', () => {
    const h=harness(); assert.equal(h.fire('beforeunload'),false);
    h.form.elements[0].value='Bob'; assert.equal(h.fire('beforeunload'),true);
    h.form.elements[0].value='Alice'; assert.equal(h.fire('beforeunload'),false);
});
test('cancelled HTMX navigation preserves dirty values', () => {
    const h=harness(); h.form.elements[0].value='Bob';
    assert.equal(h.fire('htmx:beforeRequest',{elt:{},target:{contains:()=>true}}),true);
    assert.equal(h.form.elements[0].value,'Bob');
});
test('only confirmed save redirects clear the warning', () => {
    const h=harness(); h.form.elements[0].value='Bob';
    h.fire('htmx:beforeOnLoad',{elt:h.form,xhr:{status:200,getResponseHeader:()=>null}});
    assert.equal(h.fire('beforeunload'),true);
    h.fire('htmx:beforeOnLoad',{elt:h.form,xhr:{status:200,getResponseHeader:()=>'/admin/employee/view/1'}});
    assert.equal(h.fire('beforeunload'),false);
});
test('session expiry retains values and prevents automatic redirect', () => {
    const h=harness(); h.form.elements[0].value='Bob';
    assert.equal(h.fire('htmx:beforeOnLoad',{elt:h.form,xhr:{status:401}}),true);
    assert.equal(h.form.elements[0].value,'Bob'); assert.equal(h.notice.hidden,false);
    assert.equal(h.fire('beforeunload'),true);
});
test('validation swap preserves the original baseline', () => {
    const h=harness();
    h.swap({elements:[{name:'firstName',type:'text',value:'Invalid'}],dataset:{unsaved:'true'},isConnected:true});
    assert.equal(h.fire('beforeunload'),true);
});

test('edits made during a successful save survive and require a fresh record before another save', () => {
    const h=harness(); h.form.elements[0].value='Bob';
    h.fire('htmx:beforeRequest',{elt:h.form});
    h.form.elements[0].value='Charlie';
    assert.equal(h.fire('htmx:beforeOnLoad',{elt:h.form,xhr:{status:200,getResponseHeader:()=>'/record'}}),true);
    assert.equal(h.form.elements[0].value,'Charlie');
    assert.match(h.notice.textContent,/newer edits.*not saved/);
    assert.equal(h.fire('beforeunload'),true);
    assert.equal(h.fire('htmx:beforeRequest',{elt:h.form}),true);
});

test('validation responses cannot replace edits made while the request was pending', () => {
    const h=harness(); h.form.elements[0].value='Bob';
    h.fire('htmx:beforeRequest',{elt:h.form});
    h.form.elements[0].value='Charlie';
    assert.equal(h.fire('htmx:beforeOnLoad',{elt:h.form,xhr:{status:200,getResponseHeader:()=>null}}),true);
    assert.equal(h.form.elements[0].value,'Charlie');
    assert.equal(h.fire('htmx:beforeRequest',{elt:h.form}),false);
});

test('reverting to the original value during a save still warns because the server saved a different value', () => {
    const h=harness(); h.form.elements[0].value='Bob';
    h.fire('htmx:beforeRequest',{elt:h.form});
    h.form.elements[0].value='Alice';
    h.fire('htmx:beforeOnLoad',{elt:h.form,xhr:{status:200,getResponseHeader:()=>'/record'}});
    assert.equal(h.fire('beforeunload'),true);
});

test('native submission avoids false unload warnings and returning restores protection', () => {
    const h=harness(); h.form.hasAttribute=()=>false;
    h.form.elements[0].value='Bob';
    h.fire('submit',undefined,h.form);
    assert.equal(h.fire('beforeunload'),false);
    h.fire('pageshow');
    assert.equal(h.fire('beforeunload'),true);
});
