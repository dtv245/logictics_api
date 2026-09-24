#!/usr/bin/env python3
"""
High-Performance Seed Generator for LogisticsX API.
Generates 10,000 loads and all associated entities (Roles, Terminals, Employees, Customers,
Trucks, Trips, Trip Stops, Invoices, Invoice Line Items, Payments, Documents, DVIR, HOS, Messages).
"""

import sys
import os
import uuid
import random
from datetime import datetime, timedelta, timezone

TOTAL_LOADS = int(os.environ.get("SEED_LOADS_COUNT", "10000"))
TOTAL_CUSTOMERS = int(os.environ.get("SEED_CUSTOMERS_COUNT", "1000"))
TOTAL_TRUCKS = int(os.environ.get("SEED_TRUCKS_COUNT", "300"))
TOTAL_EMPLOYEES = int(os.environ.get("SEED_EMPLOYEES_COUNT", "500"))
TOTAL_TERMINALS = 50

VN_TZ = timezone(timedelta(hours=7))

# Data pools
CITIES = [
    {"name": "Hà Nội", "state": "Hà Nội", "zip": "100000", "lat": 21.0285, "lon": 105.8542},
    {"name": "TP. Hồ Chí Minh", "state": "TP. Hồ Chí Minh", "zip": "700000", "lat": 10.8231, "lon": 106.6297},
    {"name": "Hải Phòng", "state": "Hải Phòng", "zip": "180000", "lat": 20.8458, "lon": 106.6881},
    {"name": "Đà Nẵng", "state": "Đà Nẵng", "zip": "550000", "lat": 16.0544, "lon": 108.2022},
    {"name": "Cần Thơ", "state": "Cần Thơ", "zip": "900000", "lat": 10.0452, "lon": 105.7469},
    {"name": "Đồng Nai", "state": "Đồng Nai", "zip": "810000", "lat": 10.9574, "lon": 106.8425},
    {"name": "Bình Dương", "state": "Bình Dương", "zip": "820000", "lat": 10.9804, "lon": 106.6519},
    {"name": "Bắc Ninh", "state": "Bắc Ninh", "zip": "220000", "lat": 21.1861, "lon": 106.0763},
    {"name": "Quảng Ninh", "state": "Quảng Ninh", "zip": "200000", "lat": 20.9504, "lon": 107.0734},
    {"name": "Bà Rịa - Vũng Tàu", "state": "Bà Rịa - Vũng Tàu", "zip": "790000", "lat": 10.4114, "lon": 107.1362},
    {"name": "Long An", "state": "Long An", "zip": "850000", "lat": 10.5333, "lon": 106.4000},
    {"name": "Tiền Giang", "state": "Tiền Giang", "zip": "860000", "lat": 10.3667, "lon": 106.3667},
    {"name": "Khánh Hòa", "state": "Khánh Hòa", "zip": "650000", "lat": 12.2388, "lon": 109.1967},
    {"name": "Nghệ An", "state": "Nghệ An", "zip": "460000", "lat": 18.6747, "lon": 105.6892},
    {"name": "Thanh Hóa", "state": "Thanh Hóa", "zip": "440000", "lat": 19.8067, "lon": 105.7852},
    {"name": "Lạng Sơn", "state": "Lạng Sơn", "zip": "240000", "lat": 21.8533, "lon": 106.7615},
    {"name": "Lâm Đồng", "state": "Lâm Đồng", "zip": "670000", "lat": 11.9404, "lon": 108.4583},
    {"name": "Đắk Lắk", "state": "Đắk Lắk", "zip": "630000", "lat": 12.6667, "lon": 108.0500},
    {"name": "Vĩnh Phúc", "state": "Vĩnh Phúc", "zip": "280000", "lat": 21.3089, "lon": 105.6049},
    {"name": "Hưng Yên", "state": "Hưng Yên", "zip": "160000", "lat": 20.6500, "lon": 106.0500}
]

STREETS = [
    "Đường Giải Phóng", "Đường Nguyễn Trãi", "Đường Phạm Hùng", "Đường Võ Văn Kiệt",
    "Đường Quốc lộ 1A", "Đường Quốc lộ 5", "Đường Xa Lộ Hà Nội", "Đường Nguyễn Văn Linh",
    "Đường Lê Duẩn", "Đường Cách Mạng Tháng 8", "Đường Hùng Vương", "Đường Trần Phú",
    "Đường Trường Chinh", "Đường Điện Biên Phủ", "Đường Hoàng Diệu", "Đường Lê Lợi"
]

FIRST_NAMES = [
    "Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng",
    "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý", "Đinh", "Đoàn", "Lâm", "Trịnh"
]

MIDDLE_AND_LAST_NAMES = [
    "Văn Hùng", "Thị Mai", "Minh Tuấn", "Văn Nam", "Thị Lan", "Đức Anh", "Hoàng Long",
    "Thanh Sơn", "Hữu Thắng", "Thị Hương", "Quốc Bảo", "Văn Cường", "Thị Ngọc", "Hải Đăng",
    "Thị Thảo", "Văn Dũng", "Tuấn Kiệt", "Quang Huy", "Thị Tuyết", "Minh Đức", "Đình Trọng",
    "Bảo Ngọc", "Văn Long", "Thị Dung", "Thành Đạt", "Quốc Anh", "Thị Phương", "Minh Quang"
]

COMPANY_PREFIXES = [
    "Công ty Cổ phần", "Tập đoàn", "Công ty TNHH", "Tổng Công ty", "Doanh nghiệp Tư nhân"
]

COMPANY_NAMES = [
    "Vinamilk", "Hòa Phát", "Unilever Việt Nam", "Samsung Electronics", "Masan Group",
    "Thành Thành Công", "Viettel Post", "Giao Hàng Tiết Kiệm", "Hải Hà Kotobuki", "Tôn Hoa Sen",
    "Kinh Đô", "Sabeco", "Habeco", "Dược Hậu Giang", "Nhựa Bình Minh", "Cadivi",
    "May 10", "Dệt May Phong Phú", "Thủy sản Minh Phú", "Vĩnh Hoàn", "Nông nghiệp CP",
    "Đạm Phú Mỹ", "Hóa chất Đức Giang", "Viglacera", "Gỗ An Cường", "Pin Ắc quy Đồng Nai",
    "Thép Nam Kim", "Tập đoàn TH", "Bia Hà Nội", "Bánh kẹo Bibica", "Thực phẩm Vissan"
]

COMMODITIES = [
    "Sữa tươi tiệt trùng và chế phẩm sữa", "Thép cuộn cán nóng và thép xây dựng", "Hàng tiêu dùng đóng gói FMCG",
    "Linh kiện điện tử và vi mạch", "Nước mắm và gia vị thực phẩm", "Đường tinh luyện cao cấp",
    "Bánh kẹo và thực phẩm chế biến", "Bia lon và đồ uống giải khát", "Dược phẩm và vật tư y tế",
    "Ống nhựa HDPE và phụ kiện", "Dây cáp điện công nghiệp", "Sản phẩm may mặc xuất khẩu",
    "Tôm đông lạnh xuất khẩu", "Cá tra fillet đông lạnh", "Thức ăn chăn nuôi gia súc",
    "Phân bón NPK và Urê cao cấp", "Gạch ốp lát ceramic", "Ván gỗ MDF và nội thất lắp ráp",
    "Ắc quy ô tô và xe tải", "Hạt nhựa nguyên sinh PP/PE", "Hóa chất công nghiệp cơ bản",
    "Trái cây tươi (Thanh long, Xoài, Sầu riêng)", "Nông sản khô (Hạt điều, Cà phê, Tiêu)"
]

TRUCK_MAKES_MODELS = [
    ("Hino", ["FL8J", "FG8J", "XZU720", "500 Series", "700 Series"]),
    ("Isuzu", ["GIGA 4 chân", "Forward FVR", "NPR85K", "QKR77FE"]),
    ("Hyundai", ["HD320", "HD210", "HD120SL", "Mighty EX8", "Xcient"]),
    ("Thaco", ["Auman C160", "Ollin 700", "Auman C240", "Towner"]),
    ("Daewoo", ["Novus 3 chân", "Prima 4 chân", "Maximus"]),
    ("Fuso", ["Canter 6.5", "Fighter FI", "Super Great 4 chân"])
]

TRUCK_TYPES = ["dry_van", "refrigerated", "flatbed", "tanker", "container"]
LOAD_TYPES = ["general_freight", "perishable", "hazardous", "container", "bulk"]
LOAD_SOURCES = ["portal", "manual", "api", "crm", "load_board"]

def escape_sql(val):
    if val is None:
        return "NULL"
    if isinstance(val, bool):
        return "TRUE" if val else "FALSE"
    if isinstance(val, (int, float)):
        return str(val)
    if isinstance(val, datetime):
        return f"'{val.isoformat()}'"
    val_str = str(val).replace("'", "''")
    return f"'{val_str}'"

def main():
    random.seed(42)
    now = datetime.now(VN_TZ)

    sql_lines = []
    sql_lines.append("-- Generated high-volume seed data")
    sql_lines.append("BEGIN;")

    print(f"[*] Generating {TOTAL_LOADS} loads and supporting entities...", file=sys.stderr)

    # 1. Tenant Roles & Claims
    role_admin_id = uuid.uuid4()
    role_dispatcher_id = uuid.uuid4()
    role_driver_id = uuid.uuid4()
    role_accountant_id = uuid.uuid4()
    role_safety_id = uuid.uuid4()

    roles = [
        (role_admin_id, "Quản trị viên", "Quản trị viên hệ thống", "ADMIN"),
        (role_dispatcher_id, "Điều phối viên", "Điều phối viên vận tải", "DISPATCHER"),
        (role_driver_id, "Tài xế", "Tài xế lái xe vận tải", "DRIVER"),
        (role_accountant_id, "Kế toán", "Kế toán tài chính & hóa đơn", "ACCOUNTANT"),
        (role_safety_id, "Quản lý An toàn", "Quản lý an toàn đội xe & DVIR", "SAFETY_MANAGER")
    ]

    sql_lines.append("\n-- 1. Tenant Roles")
    for r_id, r_name, r_disp, r_norm in roles:
        sql_lines.append(f"INSERT INTO tenant_roles (id, name, display_name, normalized_name) "
                         f"VALUES ({escape_sql(r_id)}, {escape_sql(r_name)}, {escape_sql(r_disp)}, {escape_sql(r_norm)}) "
                         f"ON CONFLICT (id) DO NOTHING;")

    # Role claims
    sql_lines.append("\n-- Role Claims")
    claims = [
        (role_admin_id, "permission", "full_access"),
        (role_admin_id, "permission", "tenant.manage"),
        (role_dispatcher_id, "permission", "dispatch"),
        (role_dispatcher_id, "permission", "view_loads"),
        (role_dispatcher_id, "permission", "view_trips"),
        (role_dispatcher_id, "permission", "load.confirm_status"),
        (role_driver_id, "permission", "view_assigned_loads"),
        (role_driver_id, "permission", "update_trip_status"),
        (role_accountant_id, "permission", "view_invoices"),
        (role_accountant_id, "permission", "manage_payments"),
        (role_safety_id, "permission", "view_inspections"),
        (role_safety_id, "permission", "manage_dvir")
    ]
    for r_id, c_type, c_val in claims:
        sql_lines.append(f"INSERT INTO tenant_role_claims (id, role_id, claim_type, claim_value) "
                         f"VALUES ({escape_sql(uuid.uuid4())}, {escape_sql(r_id)}, {escape_sql(c_type)}, {escape_sql(c_val)});")

    # 2. Terminals (code varchar(5))
    terminal_ids = []
    sql_lines.append("\n-- 2. Terminals")
    term_types = ["SeaPort", "Warehouse", "DistributionCenter", "CrossDock", "InlandPort"]
    for i in range(TOTAL_TERMINALS):
        t_id = uuid.uuid4()
        terminal_ids.append(t_id)
        city = CITIES[i % len(CITIES)]
        code = f"T{i+1:04d}"  # 5 chars max
        t_type = term_types[i % len(term_types)]
        name = f"Trung tâm Logistics {city['name']} #{i+1:02d}"
        sql_lines.append(
            f"INSERT INTO terminals (id, name, code, country_code, type, notes, address_city, address_country, "
            f"address_line1, address_line2, address_state, address_zip_code, created_at, created_by) VALUES ("
            f"{escape_sql(t_id)}, {escape_sql(name)}, {escape_sql(code)}, 'VN', {escape_sql(t_type)}, "
            f"{escape_sql('Trạm vận tải và kho trung chuyển chính')}, {escape_sql(city['name'])}, 'Việt Nam', "
            f"{escape_sql(f'Số {100 + i*5} {random.choice(STREETS)}')}, NULL, {escape_sql(city['state'])}, "
            f"{escape_sql(city['zip'])}, {escape_sql(now - timedelta(days=365))}, 'system') "
            f"ON CONFLICT (code) DO NOTHING;"
        )

    # 3. Employees
    admin_ids = []
    dispatcher_ids = []
    driver_ids = []
    accountant_ids = []
    all_employee_ids = []

    sql_lines.append("\n-- 3. Employees")
    num_admins = 20
    num_dispatchers = 80
    num_accountants = 40
    num_drivers = TOTAL_EMPLOYEES - (num_admins + num_dispatchers + num_accountants)

    emp_counter = 1
    for cat, count, r_id, id_list in [
        ("admin", num_admins, role_admin_id, admin_ids),
        ("dispatcher", num_dispatchers, role_dispatcher_id, dispatcher_ids),
        ("accountant", num_accountants, role_accountant_id, accountant_ids),
        ("driver", num_drivers, role_driver_id, driver_ids)
    ]:
        for _ in range(count):
            e_id = uuid.uuid4()
            id_list.append(e_id)
            all_employee_ids.append(e_id)
            fn = random.choice(FIRST_NAMES)
            ln = random.choice(MIDDLE_AND_LAST_NAMES)
            email = f"emp{emp_counter:04d}.{fn.lower()}.{ln.lower().replace(' ', '')}@logicstic.vn"
            phone = f"09{random.randint(10000000, 99999999)}"
            city = random.choice(CITIES)
            hire_date = now - timedelta(days=random.randint(30, 1000))
            is_driver = (cat == "driver")
            sal_type = "ShareOfGross" if is_driver else "Fixed"
            sal_amt = random.choice([0.18, 0.20, 0.22, 0.25]) if is_driver else random.randint(12000000, 35000000)

            sql_lines.append(
                f"INSERT INTO employees (id, email, first_name, last_name, phone_number, salary_type, status, joined_date, "
                f"role_id, salary_amount, salary_currency, address_city, address_country, address_line1, "
                f"address_state, address_zip_code, created_at, created_by) VALUES ("
                f"{escape_sql(e_id)}, {escape_sql(email)}, {escape_sql(fn)}, {escape_sql(ln)}, {escape_sql(phone)}, "
                f"{escape_sql(sal_type)}, 'Active', {escape_sql(hire_date)}, {escape_sql(r_id)}, {sal_amt}, 'VND', "
                f"{escape_sql(city['name'])}, 'Việt Nam', {escape_sql(f'Số {random.randint(1, 999)} {random.choice(STREETS)}')}, "
                f"{escape_sql(city['state'])}, {escape_sql(city['zip'])}, {escape_sql(hire_date)}, 'system');"
            )
            emp_counter += 1

    # 4. Customers
    customer_ids = []
    sql_lines.append("\n-- 4. Customers")
    for i in range(TOTAL_CUSTOMERS):
        c_id = uuid.uuid4()
        customer_ids.append(c_id)
        c_prefix = random.choice(COMPANY_PREFIXES)
        c_name = f"{c_prefix} {random.choice(COMPANY_NAMES)} {random.choice(['Miền Bắc', 'Miền Nam', 'Miền Trung', 'Việt Nam', 'Logistics', 'Thương Mại'])} #{i+1:03d}"
        email = f"contact.kh{i+1:04d}@customer.vn"
        phone = f"02{random.randint(40000000, 89999999)}"
        tax_id = f"{random.randint(1000000000, 3999999999):010d}"
        city = random.choice(CITIES)
        c_created = now - timedelta(days=random.randint(60, 720))

        sql_lines.append(
            f"INSERT INTO customers (id, name, email, phone, status, notes, tax_id, is_vat_exempt, address_city, "
            f"address_country, address_line1, address_state, address_zip_code, created_at, created_by) VALUES ("
            f"{escape_sql(c_id)}, {escape_sql(c_name)}, {escape_sql(email)}, {escape_sql(phone)}, 'Active', "
            f"{escape_sql('Đối tác khách hàng doanh nghiệp vận tải')}, {escape_sql(tax_id)}, FALSE, {escape_sql(city['name'])}, "
            f"'Việt Nam', {escape_sql(f'Tòa nhà Số {random.randint(1, 500)} {random.choice(STREETS)}')}, "
            f"{escape_sql(city['state'])}, {escape_sql(city['zip'])}, {escape_sql(c_created)}, 'system');"
        )

    # 5. Trucks (no created_at/created_by in trucks table)
    truck_ids = []
    sql_lines.append("\n-- 5. Trucks")
    plate_prefixes = ["29C", "29H", "30F", "51D", "51C", "60C", "61C", "43C", "15C", "65C"]
    for i in range(TOTAL_TRUCKS):
        tr_id = uuid.uuid4()
        truck_ids.append(tr_id)
        tr_num = f"XE-{i+1:04d}"
        tr_type = random.choice(TRUCK_TYPES)
        capacity = random.randint(12000, 32000)
        make, models = random.choice(TRUCK_MAKES_MODELS)
        model = random.choice(models)
        year = random.randint(2019, 2024)
        vin = f"VNTRK{year}{random.randint(10000000, 99999999)}"
        plate = f"{random.choice(plate_prefixes)}-{random.randint(100, 999)}.{random.randint(10, 99):02d}"
        city = random.choice(CITIES)
        main_driver = driver_ids[i % len(driver_ids)]
        sec_driver = driver_ids[(i + 1) % len(driver_ids)] if random.random() < 0.3 else None

        sql_lines.append(
            f"INSERT INTO trucks (id, number, type, vehicle_capacity, status, make, model, year, vin, license_plate, "
            f"license_plate_state, is_hazmat_placarded, main_driver_id, secondary_driver_id, adr_equipment_allowed_classes, "
            f"adr_equipment_is_adr_certified, current_address_city, current_address_country, current_address_line1, "
            f"current_address_state, current_address_zip_code, current_location_latitude, current_location_longitude) VALUES ("
            f"{escape_sql(tr_id)}, {escape_sql(tr_num)}, {escape_sql(tr_type)}, {capacity}, 'Active', "
            f"{escape_sql(make)}, {escape_sql(model)}, {year}, {escape_sql(vin)}, {escape_sql(plate)}, "
            f"{escape_sql(city['state'])}, FALSE, {escape_sql(main_driver)}, {escape_sql(sec_driver)}, '', FALSE, "
            f"{escape_sql(city['name'])}, 'Việt Nam', {escape_sql(f'Bãi đỗ xe số {random.randint(1, 50)}')}, "
            f"{escape_sql(city['state'])}, {escape_sql(city['zip'])}, {city['lat']}, {city['lon']}) "
            f"ON CONFLICT (number) DO NOTHING;"
        )

    # 6. Loads (10,000 loads)
    print(f"[*] Generating {TOTAL_LOADS} Loads & associated records...", file=sys.stderr)
    sql_lines.append(f"\n-- 6. Loads ({TOTAL_LOADS} items)")

    load_ids = []
    delivered_load_ids = []
    active_dispatched_loads = []

    for i in range(TOTAL_LOADS):
        l_id = uuid.uuid4()
        load_ids.append(l_id)

        # Distribute status
        r_stat = random.random()
        if r_stat < 0.10:
            status = "Draft"
        elif r_stat < 0.30:
            status = "Dispatched"
        elif r_stat < 0.55:
            status = "PickedUp"
        elif r_stat < 0.95:
            status = "Delivered"
        else:
            status = "Cancelled"

        if status == "Delivered":
            delivered_load_ids.append(l_id)
        elif status in ("Dispatched", "PickedUp"):
            active_dispatched_loads.append(l_id)

        commodity = random.choice(COMMODITIES)
        origin_city = random.choice(CITIES)
        dest_city = random.choice([c for c in CITIES if c["name"] != origin_city["name"]])

        distance = round(random.uniform(60.0, 1750.0), 1)
        cost = random.randint(4500000, 48000000)

        created_days_ago = random.randint(1, 180)
        l_created = now - timedelta(days=created_days_ago, hours=random.randint(1, 23))
        pickup_req = l_created + timedelta(days=random.randint(1, 3))
        deliv_req = pickup_req + timedelta(days=random.randint(1, 4))

        dispatched_at = l_created + timedelta(hours=random.randint(2, 12)) if status != "Draft" else None
        picked_up_at = dispatched_at + timedelta(hours=random.randint(4, 24)) if status in ("PickedUp", "Delivered") else None
        delivered_at = picked_up_at + timedelta(hours=random.randint(12, 72)) if status == "Delivered" else None
        cancelled_at = l_created + timedelta(hours=random.randint(1, 24)) if status == "Cancelled" else None

        cust_id = random.choice(customer_ids)
        truck_id = random.choice(truck_ids) if status != "Draft" else None
        disp_id = random.choice(dispatcher_ids) if status != "Draft" else None
        orig_term = random.choice(terminal_ids)
        dest_term = random.choice([t for t in terminal_ids if t != orig_term])
        l_type = random.choice(LOAD_TYPES)
        is_haz = (l_type == "hazardous")
        source = random.choice(LOAD_SOURCES)
        name = f"Vận chuyển {commodity[:35]} ({origin_city['name']} → {dest_city['name']}) #{i+1:05d}"

        sql_lines.append(
            f"INSERT INTO loads (id, name, type, status, distance, is_in_proximity, dispatched_at, picked_up_at, "
            f"delivered_at, cancelled_at, customer_id, assigned_truck_id, assigned_dispatcher_id, source, "
            f"requested_pickup_date, requested_delivery_date, notes, is_hazmat, hazmat_class, un_number, "
            f"origin_terminal_id, destination_terminal_id, delivery_cost_amount, delivery_cost_currency, "
            f"origin_address_city, origin_address_country, origin_address_line1, origin_address_state, "
            f"origin_address_zip_code, origin_location_latitude, origin_location_longitude, "
            f"destination_address_city, destination_address_country, destination_address_line1, "
            f"destination_address_state, destination_address_zip_code, destination_location_latitude, "
            f"destination_location_longitude, created_at, created_by) VALUES ("
            f"{escape_sql(l_id)}, {escape_sql(name)}, {escape_sql(l_type)}, {escape_sql(status)}, {distance}, FALSE, "
            f"{escape_sql(dispatched_at)}, {escape_sql(picked_up_at)}, {escape_sql(delivered_at)}, {escape_sql(cancelled_at)}, "
            f"{escape_sql(cust_id)}, {escape_sql(truck_id)}, {escape_sql(disp_id)}, {escape_sql(source)}, "
            f"{escape_sql(pickup_req)}, {escape_sql(deliv_req)}, {escape_sql('Lô hàng thương mại phân phối')}, "
            f"{is_haz}, {escape_sql('Class 3' if is_haz else None)}, {escape_sql('UN1203' if is_haz else None)}, "
            f"{escape_sql(orig_term)}, {escape_sql(dest_term)}, {cost}, 'VND', "
            f"{escape_sql(origin_city['name'])}, 'Việt Nam', {escape_sql(f'Kho số {random.randint(1, 99)} {random.choice(STREETS)}')}, "
            f"{escape_sql(origin_city['state'])}, {escape_sql(origin_city['zip'])}, {origin_city['lat']}, {origin_city['lon']}, "
            f"{escape_sql(dest_city['name'])}, 'Việt Nam', {escape_sql(f'Tổng kho nhận hàng {random.choice(STREETS)}')}, "
            f"{escape_sql(dest_city['state'])}, {escape_sql(dest_city['zip'])}, {dest_city['lat']}, {dest_city['lon']}, "
            f"{escape_sql(l_created)}, 'system');"
        )

        # 7. Trips & Trip Stops (for Dispatched, PickedUp, Delivered loads)
        if status in ("Dispatched", "PickedUp", "Delivered"):
            trip_id = uuid.uuid4()
            trip_status = "completed" if status == "Delivered" else "dispatched"
            sql_lines.append(
                f"INSERT INTO trips (id, name, total_distance, dispatched_at, completed_at, status, truck_id, created_at, created_by) VALUES ("
                f"{escape_sql(trip_id)}, {escape_sql('Chuyến ' + name)}, {distance}, {escape_sql(dispatched_at)}, "
                f"{escape_sql(delivered_at)}, {escape_sql(trip_status)}, {escape_sql(truck_id)}, {escape_sql(l_created)}, 'system');"
            )

            # Trip stop 1 (Pickup)
            sql_lines.append(
                f"INSERT INTO trip_stops (id, type, trip_id, \"order\", arrived_at, load_id, address_city, address_country, "
                f"address_line1, address_state, address_zip_code, location_latitude, location_longitude) VALUES ("
                f"{escape_sql(uuid.uuid4())}, 'pickup', {escape_sql(trip_id)}, 1, {escape_sql(picked_up_at)}, "
                f"{escape_sql(l_id)}, {escape_sql(origin_city['name'])}, 'Việt Nam', "
                f"{escape_sql(f'Kho giao {random.choice(STREETS)}')}, {escape_sql(origin_city['state'])}, "
                f"{escape_sql(origin_city['zip'])}, {origin_city['lat']}, {origin_city['lon']});"
            )

            # Trip stop 2 (Delivery)
            sql_lines.append(
                f"INSERT INTO trip_stops (id, type, trip_id, \"order\", arrived_at, load_id, address_city, address_country, "
                f"address_line1, address_state, address_zip_code, location_latitude, location_longitude) VALUES ("
                f"{escape_sql(uuid.uuid4())}, 'delivery', {escape_sql(trip_id)}, 2, {escape_sql(delivered_at)}, "
                f"{escape_sql(l_id)}, {escape_sql(dest_city['name'])}, 'Việt Nam', "
                f"{escape_sql(f'Kho nhận {random.choice(STREETS)}')}, {escape_sql(dest_city['state'])}, "
                f"{escape_sql(dest_city['zip'])}, {dest_city['lat']}, {dest_city['lon']});"
            )

            # 8. Invoices & Payments (Unique load_id constraint)
            inv_id = uuid.uuid4()
            inv_status = "Paid" if (status == "Delivered" and random.random() < 0.85) else ("Issued" if status in ("Dispatched", "PickedUp") else "Draft")
            subtotal = cost
            tax = int(subtotal * 0.10)
            total = subtotal + tax
            due_date = l_created + timedelta(days=30)

            sql_lines.append(
                f"INSERT INTO invoices (id, type, status, tax_behavior, due_date, load_id, customer_id, employee_id, "
                f"subtotal_amount, subtotal_currency, tax_total_amount, tax_total_currency, total_amount, "
                f"total_currency, created_at, created_by) VALUES ("
                f"{escape_sql(inv_id)}, 'standard', {escape_sql(inv_status)}, 'exclusive', {escape_sql(due_date)}, "
                f"{escape_sql(l_id)}, {escape_sql(cust_id)}, {escape_sql(disp_id)}, {subtotal}, 'VND', {tax}, 'VND', "
                f"{total}, 'VND', {escape_sql(l_created)}, 'system');"
            )

            # Invoice line item
            sql_lines.append(
                f"INSERT INTO invoice_line_items (id, invoice_id, description, type, quantity, \"order\", "
                f"tax_rate_percent, tax_amount, amount_amount, amount_currency) VALUES ("
                f"{escape_sql(uuid.uuid4())}, {escape_sql(inv_id)}, {escape_sql(f'Cước vận chuyển hàng: {commodity[:40]}')}, "
                f"'freight', 1, 1, 10.0, {tax}, {subtotal}, 'VND');"
            )

            # Payment (if Paid)
            if inv_status == "Paid":
                pmt_id = uuid.uuid4()
                pmt_date = (delivered_at or l_created) + timedelta(days=random.randint(1, 10))
                sql_lines.append(
                    f"INSERT INTO payments (id, status, description, reference_number, recorded_at, invoice_id, "
                    f"amount_amount, amount_currency, billing_address_city, billing_address_country, billing_address_line1, "
                    f"billing_address_state, billing_address_zip_code, created_at, created_by) VALUES ("
                    f"{escape_sql(pmt_id)}, 'completed', {escape_sql(f'Thanh toán chuyển khoản hóa đơn #{i+1:05d}')}, "
                    f"{escape_sql(f'PMT-{i+1:06d}')}, {escape_sql(pmt_date)}, {escape_sql(inv_id)}, {total}, 'VND', "
                    f"{escape_sql(dest_city['name'])}, 'Việt Nam', {escape_sql(f'Số {random.randint(10, 200)} {random.choice(STREETS)}')}, "
                    f"{escape_sql(dest_city['state'])}, {escape_sql(dest_city['zip'])}, {escape_sql(pmt_date)}, 'system');"
                )

        # 9. Documents (BOL, POD, Inspection)
        if random.random() < 0.60:
            doc_id = uuid.uuid4()
            doc_type = random.choice(["bol", "pod", "invoice_copy", "security_check", "weight_ticket"])
            fn = f"{doc_type}_load_{i+1:05d}.pdf"
            uploader = random.choice(all_employee_ids)
            sql_lines.append(
                f"INSERT INTO documents (id, owner_type, file_name, original_file_name, content_type, file_size_bytes, "
                f"blob_path, blob_container, type, status, uploaded_by_id, load_id, description, created_at, created_by) VALUES ("
                f"{escape_sql(doc_id)}, 'load', {escape_sql(fn)}, {escape_sql(fn)}, 'application/pdf', "
                f"{random.randint(80000, 2500000)}, {escape_sql(f'seed/loads/{l_id}/{fn}')}, 'seed-demo', "
                f"{escape_sql(doc_type)}, 'active', {escape_sql(uploader)}, {escape_sql(l_id)}, "
                f"{escape_sql('Chứng từ vận tải điện tử có chữ ký số')}, {escape_sql(l_created)}, 'system');"
            )

        # 10. Conversations & Messages
        if random.random() < 0.30 and status != "Draft":
            conv_id = uuid.uuid4()
            sql_lines.append(
                f"INSERT INTO conversations (id, name, load_id, is_tenant_chat, created_at) VALUES ("
                f"{escape_sql(conv_id)}, {escape_sql('Trao đổi điều phối: ' + name[:40])}, {escape_sql(l_id)}, FALSE, {escape_sql(l_created)});"
            )

            # Participants
            part_driver = random.choice(driver_ids)
            part_disp = disp_id or random.choice(dispatcher_ids)
            for p_emp in [part_driver, part_disp]:
                sql_lines.append(
                    f"INSERT INTO conversation_participants (id, conversation_id, employee_id, joined_at, is_muted) VALUES ("
                    f"{escape_sql(uuid.uuid4())}, {escape_sql(conv_id)}, {escape_sql(p_emp)}, {escape_sql(l_created)}, FALSE);"
                )

            # Messages
            msg_pool = [
                "Đã nhận thông tin lệnh vận chuyển, đang kiểm tra xe.",
                "Hàng đã bốc xếp xong, chuẩn bị xuất bến đúng lịch trình.",
                "Đang di chuyển trên tuyến cao tốc, giao thông thông suốt.",
                "Đã đến điểm trả hàng, đang hoàn tất biên bản bàn giao POD.",
                "Khách hàng đã ký nhận đủ hàng và chứng từ đầy đủ."
            ]
            for m_idx, m_txt in enumerate(random.sample(msg_pool, k=random.randint(2, 4))):
                m_sender = part_driver if m_idx % 2 == 0 else part_disp
                m_time = l_created + timedelta(hours=m_idx * 3 + 1)
                sql_lines.append(
                    f"INSERT INTO messages (id, conversation_id, sender_id, content, sent_at, is_deleted) VALUES ("
                    f"{escape_sql(uuid.uuid4())}, {escape_sql(conv_id)}, {escape_sql(m_sender)}, {escape_sql(m_txt)}, "
                    f"{escape_sql(m_time)}, FALSE);"
                )

    # 11. DVIR Reports (Vehicle inspection logs)
    print("[*] Generating DVIR Reports and HOS Logs...", file=sys.stderr)
    sql_lines.append("\n-- 11. DVIR Reports")
    for i in range(2500):
        dvir_id = uuid.uuid4()
        tr_id = random.choice(truck_ids)
        dr_id = random.choice(driver_ids)
        insp_date = now - timedelta(days=random.randint(1, 150), hours=random.randint(1, 20))
        d_type = random.choice(["pre_trip", "post_trip"])
        has_def = (random.random() < 0.08)
        sql_lines.append(
            f"INSERT INTO dvir_reports (id, truck_id, driver_id, type, status, inspection_date, has_defects, "
            f"driver_notes, odometer_reading, created_at, created_by) VALUES ("
            f"{escape_sql(dvir_id)}, {escape_sql(tr_id)}, {escape_sql(dr_id)}, {escape_sql(d_type)}, 'completed', "
            f"{escape_sql(insp_date)}, {has_def}, {escape_sql('Kiểm tra an toàn định kỳ trước/sau chuyến đi')}, "
            f"{random.randint(25000, 380000)}, {escape_sql(insp_date)}, 'system');"
        )

    # 12. HOS Logs (Hours of service)
    sql_lines.append("\n-- 12. HOS Logs")
    hos_statuses = ["driving", "on_duty", "sleeper_berth", "off_duty"]
    for i in range(4000):
        hos_id = uuid.uuid4()
        dr_id = random.choice(driver_ids)
        h_date = now - timedelta(days=random.randint(1, 90))
        h_status = random.choice(hos_statuses)
        dur = random.randint(60, 480)
        h_start = h_date + timedelta(hours=random.randint(6, 18))
        h_end = h_start + timedelta(minutes=dur)
        sql_lines.append(
            f"INSERT INTO hos_logs (id, employee_id, log_date, duty_status, start_time, end_time, duration_minutes, "
            f"location, provider_type, remark) VALUES ("
            f"{escape_sql(hos_id)}, {escape_sql(dr_id)}, {escape_sql(h_date)}, {escape_sql(h_status)}, "
            f"{escape_sql(h_start)}, {escape_sql(h_end)}, {dur}, {escape_sql('Trạm dừng nghỉ cao tốc')}, "
            f"'samsara', {escape_sql('Ghi nhận tự động từ thiết bị ELD')});"
        )

    sql_lines.append("COMMIT;")

    # Write to temp file or stdout
    output_path = os.environ.get("SEED_SQL_FILE", "/tmp/seed_10k.sql")
    print(f"[*] Writing {len(sql_lines)} SQL statements to {output_path}...", file=sys.stderr)
    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n".join(sql_lines) + "\n")

    print(f"[✓] SQL Seed file generated successfully: {output_path}", file=sys.stderr)

if __name__ == "__main__":
    main()
