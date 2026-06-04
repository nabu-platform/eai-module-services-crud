## Fragment: crud.xml

Use `crud.xml` to configure the CRUD artifact itself.

The CRUD artifact generates child artifacts under `services.*`, `batchServices.*` and `types.*` to provide executable services and generated structures.
It starts from a structure and then adds services to perform basic CRUD operations.
Depending on the provider this can go to the database (default), OData, hubspot, odoo,...
CRUD will generate extended structures to that implement restrict policies if needed.

General rules:
- Scalar lists are represented by repeating the same element multiple times, for example `createRole` or `createBlacklistFields`.
- Complex list entries such as `foreignFields`, `filters` and `views` are nested XML elements.

### Root element
- `crud`: Root element for the CRUD artifact configuration.

### Identity and routing
- `name`: Functional name of the CRUD definition. Used by parts of the system such as permissions and components. If omitted, the wrapped document/type name is used.
- `basePath`: Base REST path for the generated CRUD REST endpoints.

### Core artifact references
- `coreType`: Main defined type wrapped by this CRUD artifact.
- `provider`: CRUD provider artifact that supplies the underlying create, update, list and delete implementation, one of: {{KNOWN_CRUD_PROVIDERS}}
- `connection`: Default data source provider artifact used for this CRUD. Must be a `jdbcPool` artifact.
- `changeTracker`: Optional service implementing `be.nabu.libs.services.jdbc.api.ChangeTracker.track` for auditing or change tracking.

### Blacklist and regeneration behavior
- `createBlacklistFields`: Fields excluded from generated create shapes.
- `updateBlacklistFields`: Fields excluded from generated update shapes.
- `listBlacklistFields`: Fields excluded from generated list/get output shapes.
- `updateRegenerateFields`: Fields that should be regenerated during update handling (e.g. a `lastModified`)

### Enriched foreign fields
- `foreignFields`: Additional fields injected in list/get output based on foreign key data.
	- `foreignName`: Name of the injected output field.
	- `localName`: Local field used as input for the lookup.
	- `foreignKey`: Foreign-key expression describing how the external value is resolved. A foreign key has the format: `<structureId>:<field>`

### Additional behavior configuration
- `filters`: Default configured CRUD filters.
- `views`: Additional list/get views beside the default view. These create additional generated services.

### Filters

Filters (unless explicitly defined otherwise) are an `AND`, not an `OR`.

### Roles
- `createRole`: Role allowed to create records.
- `updateRole`: Role allowed to update records.
- `listRole`: Role allowed to list records.
- `deleteRole`: Role allowed to delete records.

### Permissions
- `createPermission`: Permission token required for create.
- `updatePermission`: Permission token required for update.
- `listPermission`: Permission token required for list.
- `getPermission`: Permission token required for get.
- `deletePermission`: Permission token required for delete.

### Security context strategy
These fields control how permission context is derived. Normally you pick one approach instead of combining many.

- `permissionContextType`: Supported values are `SERVICE_CONTEXT`, `WEB_APPLICATION`, `PROJECT`, `GLOBAL` and `AUTHORIZATION_SERVICE_CONTEXT`.
- `securityContextField`: Field on the wrapped type used to determine the permission context.
- `securityContextFieldPrefix`: Optional prefix used together with `securityContextField`.
- `customSecurityContext`: Explicit custom permission context value.
- `primaryKeySecurityContext`: If true, derive the permission context from the primary key.
- `primaryKeySecurityContextPrefix`: Optional prefix used when `primaryKeySecurityContext` is enabled.

### REST and request behavior
- `restLimitToUser`: Defaults to `true`. Limits REST behavior to the active user context where applicable/possible.
- `allowHeaderAsQueryParameter`: Defaults to `true`. Allows selected headers to be passed as query parameters for download-oriented use cases.

### Language behavior
- `useLanguage`: Enables language-aware behavior and exposes language parameters where supported.
- `useExplicitLanguage`: If language support is enabled, require explicit language selection instead of implicit user language.

### Output shaping and side effects
- `useListOutputForCreate`: After create, return the list/get style output instead of the raw create output.
- `useListOutputForUpdate`: After update, return the list/get style output instead of the raw update output.

### Listing behavior
- `defaultTotalCount`: Default total-count strategy for list operations. Value must be `EXACT`, `ESTIMATE` or `NONE`. Estimate is rarely used because it is often very wrong.
- `maxLimit`: Optional maximum limit for list operations.

### Example
```xml
<crud>
	<name>customer</name>
	<basePath>/api/customers</basePath>
	<coreType>my.app.types.Customer</coreType>
	<provider>my.app.providers.customerCrudProvider</provider>
	<connection>my.app.jdbc.main</connection>
	<changeTracker>my.app.services.audit.trackChange</changeTracker>
	<createBlacklistFields>id</createBlacklistFields>
	<createBlacklistFields>created</createBlacklistFields>
	<updateBlacklistFields>created</updateBlacklistFields>
	<listBlacklistFields>internalNotes</listBlacklistFields>
	<updateRegenerateFields>updated</updateRegenerateFields>
	<foreignFields>
		<foreignName>ownerCompany:id</foreignName>
		<localName>companyId</localName>
		<foreignKey>my.app.types.Company:id</foreignKey>
	</foreignFields>
	<createRole>admin</createRole>
	<updateRole>admin</updateRole>
	<listRole>user</listRole>
	<deleteRole>admin</deleteRole>
	<createPermission>customer.create</createPermission>
	<updatePermission>customer.update</updatePermission>
	<listPermission>customer.list</listPermission>
	<getPermission>customer.get</getPermission>
	<deletePermission>customer.delete</deletePermission>
	<securityContextField>ownerId</securityContextField>
	<securityContextFieldPrefix>customer</securityContextFieldPrefix>
	<permissionContextType>PROJECT</permissionContextType>
	<useLanguage>true</useLanguage>
	<allowHeaderAsQueryParameter>true</allowHeaderAsQueryParameter>
	<restLimitToUser>true</restLimitToUser>
	<useListOutputForCreate>true</useListOutputForCreate>
	<useListOutputForUpdate>true</useListOutputForUpdate>
	<defaultTotalCount>EXACT</defaultTotalCount>
	<maxLimit>250</maxLimit>
</crud>
```
