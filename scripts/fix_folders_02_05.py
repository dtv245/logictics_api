import re

with open("scripts/build_full_suite.py") as f:
    code = f.read()

# Replace get_folder_02
f02_pattern = re.compile(r'def get_folder_02\(\):.*?(?=def get_folder_03)', re.DOTALL)

new_f02 = '''def get_folder_02():
    items = []
    # Roles
    items.append(make_req("02.1 Create Role", "POST", "/api/roles", "Create role with claims", body={
        "name": "DISPATCHER_LEAD_{{$timestamp}}",
        "displayName": "Dispatcher Role",
        "claims": [{
            "claimType": "PERMISSION",
            "claimValue": "ROLE_DISPATCHER"
        }]
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("roleId", d.id);
    console.log("Saved roleId:", d.id);
}
"""))
    items.append(make_req("02.2 List Roles", "GET", "/api/roles", "List all roles", qparams={"page": 1, "pageSize": 20}, test=TEST_BASE))
    items.append(make_req("02.3 Get Role by ID", "GET", "/api/roles/{{roleId}}", "Get role details", test=TEST_BASE))
    items.append(make_req("02.4 Update Role", "PUT", "/api/roles/{{roleId}}", "Update role details", body={
        "name": "DISPATCHER_LEAD_{{$timestamp}}",
        "displayName": "Senior Dispatcher Role",
        "claims": [{
            "claimType": "PERMISSION",
            "claimValue": "ROLE_DISPATCHER"
        }]
    }, test=TEST_BASE))

    # Employees
    items.append(make_req("02.5 Create Dispatcher Employee", "POST", "/api/employees", "Create dispatcher employee", body={
        "firstName": "Alex",
        "lastName": "Dispatcher",
        "email": "dispatcher_{{$timestamp}}@example.com",
        "phoneNumber": "+1-555-0100",
        "joinedDate": "2026-01-01T00:00:00Z",
        "salaryAmount": 5000.0,
        "salaryCurrency": "{{enum_currency}}",
        "salaryType": "{{enum_salaryType}}",
        "status": "{{enum_employeeStatus}}",
        "roleId": "{{roleId}}",
        "addressLine1": "100 Logistics Blvd",
        "addressCity": "Los Angeles",
        "addressState": "CA",
        "addressZipCode": "90001",
        "addressCountry": "{{enum_country}}"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("dispatcherEmployeeId", d.id);
    console.log("Saved dispatcherEmployeeId:", d.id);
}
"""))

    items.append(make_req("02.6 Create Driver Employee", "POST", "/api/employees", "Create driver employee", body={
        "firstName": "John",
        "lastName": "Driver",
        "email": "driver_{{$timestamp}}@example.com",
        "phoneNumber": "+1-555-0199",
        "joinedDate": "2026-01-01T00:00:00Z",
        "salaryAmount": 4500.0,
        "salaryCurrency": "{{enum_currency}}",
        "salaryType": "{{enum_salaryType}}",
        "status": "{{enum_employeeStatus}}",
        "roleId": "{{roleId}}",
        "addressLine1": "200 Harbor Way",
        "addressCity": "Long Beach",
        "addressState": "CA",
        "addressZipCode": "90802",
        "addressCountry": "{{enum_country}}"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("driverEmployeeId", d.id);
    pm.environment.set("driverId", d.id);
    console.log("Saved driverEmployeeId & driverId:", d.id);
}
"""))

    items.append(make_req("02.7 List Employees", "GET", "/api/employees", "List employees", qparams={"search": "", "page": 1, "pageSize": 20}, test=TEST_BASE))
    items.append(make_req("02.8 Get Employee by ID", "GET", "/api/employees/{{driverEmployeeId}}", "Get employee details", test=TEST_BASE))
    items.append(make_req("02.9 List Drivers", "GET", "/api/drivers", "List active drivers", qparams={"search": "", "page": 1, "pageSize": 20}, test=TEST_BASE))
    items.append(make_req("02.10 Get Driver by ID", "GET", "/api/drivers/{{driverId}}", "Get driver view", test=TEST_BASE))

    # Customers
    items.append(make_req("02.11 Create Customer", "POST", "/api/customers", "Create shipper customer", body={
        "name": "Acme Logistics Corp {{$timestamp}}",
        "email": "billing_{{$timestamp}}@acme.com",
        "phone": "+1-800-555-0144",
        "status": "{{enum_customerStatus}}",
        "isVatExempt": False,
        "taxId": "TAX-998877",
        "addressLine1": "500 Industrial Pkwy",
        "addressCity": "San Francisco",
        "addressState": "CA",
        "addressZipCode": "94107",
        "addressCountry": "{{enum_country}}",
        "notes": "Premium enterprise account"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("customerId", d.id);
    console.log("Saved customerId:", d.id);
}
"""))
    items.append(make_req("02.12 List Customers", "GET", "/api/customers", "List customers", qparams={"page": 1, "pageSize": 20}, test=TEST_BASE))
    items.append(make_req("02.13 Get Customer by ID", "GET", "/api/customers/{{customerId}}", "Get customer details", test=TEST_BASE))
    items.append(make_req("02.14 Update Customer", "PUT", "/api/customers/{{customerId}}", "Update customer", body={
        "name": "Acme Logistics Global",
        "email": "billing@acme.com",
        "status": "{{enum_customerStatus}}",
        "isVatExempt": False,
        "notes": "Updated SLA Tier 1"
    }, test=TEST_BASE))
    items.append(make_req("02.15 Get Customer Balance", "GET", "/api/customers/{{customerId}}/balance", "Check customer account balance", test=TEST_BASE))

    # Trucks
    items.append(make_req("02.16 Create Truck", "POST", "/api/trucks", "Register semi-truck vehicle", body={
        "number": "TRK-{{$timestamp}}",
        "make": "Freightliner",
        "model": "Cascadia",
        "year": 2023,
        "licensePlate": "CA{{$randomInt}}",
        "licensePlateState": "CA",
        "status": "{{enum_truckStatus}}",
        "type": "{{enum_truckType}}",
        "vehicleCapacity": 45000.0,
        "mainDriverId": "{{driverId}}",
        "isHazmatPlacarded": False
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("truckId", d.id);
    pm.environment.set("truckVersion", String(d.version !== undefined ? d.version : 0));
    console.log("Saved truckId:", d.id, "version:", d.version);
}
"""))
    items.append(make_req("02.17 List Trucks", "GET", "/api/trucks", "List trucks", qparams={"search": "", "page": 1, "pageSize": 20}, test=TEST_BASE))
    items.append(make_req("02.18 Get Truck by ID", "GET", "/api/trucks/{{truckId}}", "Get truck details and latest version", test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("truckVersion", String(d.version));
}
"""))
    items.append(make_req("02.19 Update Truck", "PUT", "/api/trucks/{{truckId}}", "Update truck with expectedVersion", body={
        "expectedVersion": 0,
        "number": "TRK-UPD-{{$timestamp}}",
        "make": "Freightliner",
        "model": "Cascadia Evolution",
        "year": 2024,
        "status": "{{enum_truckStatus}}",
        "type": "{{enum_truckType}}",
        "vehicleCapacity": 46000.0,
        "mainDriverId": "{{driverId}}",
        "isHazmatPlacarded": False
    }, pre="""
const v = Number(pm.environment.get("truckVersion")) || 0;
const body = JSON.parse(pm.request.body.raw);
body.expectedVersion = v;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("truckVersion", String(d.version));
}
"""))

    return {"name": "02_MasterData", "item": items}
'''

assert f02_pattern.search(code), "f02 pattern not found"
code = f02_pattern.sub(new_f02, code)

# Replace get_folder_05
f05_pattern = re.compile(r'def get_folder_05\(\):.*?(?=def get_folder_06)', re.DOTALL)

new_f05 = '''def get_folder_05():
    items = []
    items.append(make_req("05.1 Create Load", "POST", "/api/loads", "Create freight load for shipper", body={
        "name": "Full Truckload Shipment #{{$timestamp}}",
        "status": "{{enum_loadStatus}}",
        "type": "{{enum_loadType}}",
        "source": "{{enum_loadSource}}",
        "customerId": "{{customerId}}",
        "distance": 450.0,
        "isInProximity": False,
        "deliveryCostAmount": 1500.0,
        "deliveryCostCurrency": "{{enum_currency}}",
        "weight": 35000.0,
        "length": 53.0,
        "isHazmat": False,
        "originAddressLine1": "100 Port Way",
        "originAddressCity": "Los Angeles",
        "originAddressState": "CA",
        "originAddressZipCode": "90001",
        "originAddressCountry": "{{enum_country}}",
        "originLocationLatitude": 34.0522,
        "originLocationLongitude": -118.2437,
        "destinationAddressLine1": "500 Market St",
        "destinationAddressCity": "San Francisco",
        "destinationAddressState": "CA",
        "destinationAddressZipCode": "94105",
        "destinationAddressCountry": "{{enum_country}}",
        "destinationLocationLatitude": 37.7749,
        "destinationLocationLongitude": -122.4194,
        "requestedPickupDate": "2026-10-15T08:00:00Z",
        "requestedDeliveryDate": "2026-10-16T18:00:00Z"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("loadId", d.id);
    pm.environment.set("loadVersion", String(d.version !== undefined ? d.version : 0));
    console.log("Saved loadId:", d.id, "version:", d.version);
}
"""))

    items.append(make_req("05.2 Get Load by ID", "GET", "/api/loads/{{loadId}}", "Get load details & version", test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("loadVersion", String(d.version));
}
"""))

    items.append(make_req("05.3 Update Load", "PUT", "/api/loads/{{loadId}}", "Update load with expectedVersion", body={
        "expectedVersion": 0,
        "name": "Full Truckload Shipment #{{$timestamp}} (Updated)",
        "status": "{{enum_loadStatus}}",
        "type": "{{enum_loadType}}",
        "source": "{{enum_loadSource}}",
        "customerId": "{{customerId}}",
        "distance": 450.0,
        "isInProximity": False,
        "deliveryCostAmount": 1500.0,
        "deliveryCostCurrency": "{{enum_currency}}",
        "isHazmat": False,
        "originAddressLine1": "100 Port Way",
        "originAddressCity": "Los Angeles",
        "originAddressState": "CA",
        "originAddressZipCode": "90001",
        "originAddressCountry": "{{enum_country}}",
        "originLocationLatitude": 34.0522,
        "originLocationLongitude": -118.2437,
        "destinationAddressLine1": "500 Market St",
        "destinationAddressCity": "San Francisco",
        "destinationAddressState": "CA",
        "destinationAddressZipCode": "94105",
        "destinationAddressCountry": "{{enum_country}}",
        "destinationLocationLatitude": 37.7749,
        "destinationLocationLongitude": -122.4194,
        "requestedPickupDate": "2026-10-15T08:00:00Z",
        "requestedDeliveryDate": "2026-10-16T18:00:00Z",
        "notes": "Driver gate pass confirmed"
    }, pre="""
const v = Number(pm.environment.get("loadVersion")) || 0;
const body = JSON.parse(pm.request.body.raw);
body.expectedVersion = v;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("loadVersion", String(d.version));
}
"""))

    items.append(make_req("05.4 Set Requested Pickup Business Date", "POST", "/api/loads/{{loadId}}/requested-pickup-business-date", "Set operational pickup date", body={
        "requestedPickupBusinessDate": "2026-10-15",
        "provenance": {
            "reasonCode": "SHIPPER_SCHEDULE_CONFIRMED",
            "reason": "Shipper schedule request",
            "source": "SHIPPER_PORTAL"
        }
    }, test=TEST_BASE))

    return {"name": "05_Load", "item": items}
'''

assert f05_pattern.search(code), "f05 pattern not found"
code = f05_pattern.sub(new_f05, code)

with open("scripts/build_full_suite.py", "w") as f:
    f.write(code)

print("Updated Folder 02 and Folder 05 successfully!")
