# Independently checking entity permissions

The disposable sample includes `restricted / password`, a read-only account scoped to Team Members. These are public demo credentials, never credentials for a production installation.

1. Sign in as `restricted`. Open Team Members and Alice Vance.
2. Verify that department names such as Engineering are absent and the skills collection says **Related records unavailable.** An inaccessible collection must not be presented as empty.
3. Visit `/admin/department`, `/admin/department/view/1`, `/admin/skill` and `/admin/employee/view/1/related/skills` directly. Each must return 403.
4. Search for Engineering. No department result should appear.
5. Editing, action previews and activity history are denied. Log out and sign in as `admin / admin` to compare the permitted journey.

The sample supplies its own `AdminPermissionEvaluator` bean, extending the standard evaluator while restricting entity visibility for `ROLE_RESTRICTED`. The default library policy is unchanged. `ROLE_USER` remains read-only across entities; `ROLE_ADMIN` retains normal sample permissions. Entity restrictions are not row-level tenant isolation or field-level RBAC.

Automated coverage: `EntityPermissionIntegrationTest` exercises the real sample role policy without stubbing permission decisions for this journey. `scripts/smoke_demo.py` additionally checks the actual account login and denial paths in a running disposable demo. Use the configured admin prefix instead of `/admin` when testing a custom-path installation manually.
