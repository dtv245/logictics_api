# API Authorization

Authorization is enforced by Spring Security after the JWT signature, issuer, audience, expiry,
and `tenant` claim have been validated.

## Roles

The API recognizes `SuperAdmin`, `Owner`, `Manager`, `Dispatcher`, and `Driver`. Role names are
case-insensitive and are normalized to Spring Security `ROLE_*` authorities.

## Endpoint Matrix

| Resource | Read | Create/update/delete |
|----------|------|----------------------|
| Roles, employees | SuperAdmin, Owner | SuperAdmin, Owner |
| Customers | SuperAdmin, Owner, Manager | SuperAdmin, Owner, Manager |
| Invoices, payments | SuperAdmin, Owner, Manager, Dispatcher | SuperAdmin, Owner, Manager |
| Loads, trips | All tenant roles | SuperAdmin, Owner, Manager, Dispatcher |
| Load pickup/delivery | All tenant roles | All tenant roles |
| Documents | All tenant roles | Upload: all; other changes: SuperAdmin, Owner, Manager, Dispatcher |
| Drivers, trucks | SuperAdmin, Owner, Manager, Dispatcher | SuperAdmin, Owner, Manager, Dispatcher |
| Inspections, messages, notifications | All tenant roles | All tenant roles |

Rules are evaluated from most specific to least specific. An authenticated caller without the
required role receives `403 ACCESS_DENIED`; a missing or invalid token receives
`401 UNAUTHENTICATED`.

## Tenant Boundary

The validated JWT `tenant` claim selects the tenant datasource when multi-tenancy is enabled. The
claim is mandatory so an authenticated request cannot fall through without a tenant boundary.

Endpoint roles are only the first authorization layer. Record-level rules such as “Driver can view
only assigned loads” must also be applied in service/repository queries when driver-specific list
endpoints are introduced.
