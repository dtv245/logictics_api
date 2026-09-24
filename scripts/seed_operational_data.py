#!/usr/bin/env python3
"""
Seed generator for Operational Efficiency and Fleet Health data in LogisticsX.
Populates:
- vehicle_mileage_readings
- maintenance_schedules
- maintenance_records
- load_condition_reports & condition_defects
- expenses
"""

import uuid
import random
from datetime import datetime, timedelta, timezone

def generate_sql():
    lines = []
    lines.append("BEGIN;")

    # 1. Fetch trucks, loads, employees
    # We will generate SQL that joins and inserts directly
    print("Generating SQL script for operational and fleet health seed...")

    # Vehicle Mileage Readings & Trucks current_odometer update
    lines.append("""
-- 1. Vehicle Mileage Readings
DO $$
DECLARE
    t RECORD;
    reading_date timestamptz;
    base_odometer INT;
    cur_odometer INT;
    total_truck_miles INT;
    step_miles INT;
    reading_id uuid;
    i INT;
BEGIN
    DELETE FROM vehicle_mileage_readings;
    
    FOR t IN 
        SELECT trk.id, COALESCE(SUM(l.distance), 10000)::int AS loaded_dist 
        FROM trucks trk 
        LEFT JOIN loads l ON l.assigned_truck_id = trk.id AND lower(l.status) = 'delivered' 
        GROUP BY trk.id 
    LOOP
        base_odometer := 50000 + (floor(random() * 50000))::int;
        cur_odometer := base_odometer;
        -- Total miles includes loaded miles + ~18% deadhead/empty miles
        total_truck_miles := GREATEST((t.loaded_dist * 1.18)::int, 12000);
        
        -- Generate 10 readings from March 2026 to Sept 2026
        FOR i IN 1..10 LOOP
            reading_date := '2026-03-25 00:00:00+00'::timestamptz + ((i - 1) * 20 || ' days')::interval + ((random() * 86400)::int || ' seconds')::interval;
            step_miles := (total_truck_miles / 9)::int + (floor(random() * 200) - 100)::int;
            IF i = 1 THEN
                cur_odometer := base_odometer;
            ELSE
                cur_odometer := cur_odometer + step_miles;
            END IF;
            
            INSERT INTO vehicle_mileage_readings (id, truck_id, reading_value, recorded_at, source)
            VALUES (gen_random_uuid(), t.id, cur_odometer, reading_date, 'ELD');
        END LOOP;
        
        -- Update truck's current odometer
        UPDATE trucks 
        SET current_odometer = cur_odometer,
            current_odometer_recorded_at = reading_date
        WHERE id = t.id;
    END LOOP;
END $$;
""")

    # 2. Maintenance Schedules
    lines.append("""
-- 2. Maintenance Schedules
DO $$
DECLARE
    t RECORD;
    sched_id uuid;
    cur_odo INT;
BEGIN
    DELETE FROM maintenance_schedules;
    
    FOR t IN SELECT id, current_odometer FROM trucks LOOP
        cur_odo := COALESCE(t.current_odometer, 100000);
        
        -- Schedule 1: Oil Change (every 15,000 miles / 90 days)
        INSERT INTO maintenance_schedules (
            id, truck_id, maintenance_type, interval_type, mileage_interval, days_interval,
            last_service_mileage, last_service_date, next_due_mileage, next_due_date, is_active, notes, created_at
        ) VALUES (
            gen_random_uuid(), t.id, 'Thay dầu và lọc dầu động cơ', 'mileage', 15000, 90,
            cur_odo - 5000, '2026-06-15 00:00:00+00'::timestamptz, cur_odo + 10000, 
            CASE WHEN random() < 0.06 THEN '2026-08-01 00:00:00+00'::timestamptz ELSE '2026-11-15 00:00:00+00'::timestamptz END,
            TRUE, 'Bảo dưỡng định kỳ động cơ', '2026-03-25 00:00:00+00'::timestamptz
        );

        -- Schedule 2: Brake & Tire Inspection (every 30,000 miles / 180 days)
        INSERT INTO maintenance_schedules (
            id, truck_id, maintenance_type, interval_type, mileage_interval, days_interval,
            last_service_mileage, last_service_date, next_due_mileage, next_due_date, is_active, notes, created_at
        ) VALUES (
            gen_random_uuid(), t.id, 'Kiểm tra hệ thống phanh và lốp xe', 'mileage', 30000, 180,
            cur_odo - 12000, '2026-04-10 00:00:00+00'::timestamptz, cur_odo + 18000,
            CASE WHEN random() < 0.05 THEN '2026-08-20 00:00:00+00'::timestamptz ELSE '2026-12-01 00:00:00+00'::timestamptz END,
            TRUE, 'Kiểm tra an toàn phanh và độ mòn gai lốp', '2026-03-25 00:00:00+00'::timestamptz
        );

        -- Schedule 3: Annual DOT Inspection
        INSERT INTO maintenance_schedules (
            id, truck_id, maintenance_type, interval_type, days_interval,
            last_service_date, next_due_date, is_active, notes, created_at
        ) VALUES (
            gen_random_uuid(), t.id, 'Đăng kiểm và kiểm định an toàn kỹ thuật', 'time', 365,
            '2025-11-20 00:00:00+00'::timestamptz, 
            CASE WHEN random() < 0.04 THEN '2026-08-15 00:00:00+00'::timestamptz ELSE '2026-11-20 00:00:00+00'::timestamptz END,
            TRUE, 'Kiểm định an toàn định kỳ hàng năm', '2026-03-25 00:00:00+00'::timestamptz
        );
    END LOOP;
END $$;
""")

    # 3. Maintenance Records (Planned, Unplanned, Breakdowns)
    lines.append("""
-- 3. Maintenance Records
DO $$
DECLARE
    t RECORD;
    emp_id uuid;
    rec_date timestamptz;
    dt_start timestamptz;
    dt_end timestamptz;
    labor numeric(18,2);
    parts numeric(18,2);
    total numeric(18,2);
    i INT;
    rnd FLOAT;
BEGIN
    DELETE FROM maintenance_records;
    SELECT id INTO emp_id FROM employees LIMIT 1;
    
    FOR t IN SELECT id, current_odometer FROM trucks LOOP
        -- For each truck, create 1-3 maintenance events over the 6 months
        FOR i IN 1..(1 + (floor(random() * 3))::int) LOOP
            rec_date := '2026-04-01 00:00:00+00'::timestamptz + ((random() * 160)::int || ' days')::interval;
            rnd := random();
            
            IF rnd < 0.70 THEN
                -- Planned PM
                labor := (1500000 + (floor(random() * 1500000))::int);
                parts := (3000000 + (floor(random() * 4000000))::int);
                total := labor + parts;
                
                INSERT INTO maintenance_records (
                    id, truck_id, maintenance_type, service_date, odometer_reading,
                    vendor_name, invoice_number, labor_cost, parts_cost, total_cost,
                    total_cost_currency, is_unplanned, is_breakdown, description,
                    work_performed, performed_by_id, created_at
                ) VALUES (
                    gen_random_uuid(), t.id, 'Bảo dưỡng định kỳ', rec_date, COALESCE(t.current_odometer, 80000) - (10 - i) * 1000,
                    'Trung tâm dịch vụ Hino / Isuzu', 'INV-PM-' || floor(random() * 100000)::text,
                    labor, parts, total, 'VND', FALSE, FALSE, 'Bảo dưỡng định kỳ theo lịch',
                    'Thay dầu động cơ, lọc dầu, lọc gió và kiểm tra gầm', emp_id, rec_date
                );
            ELSIF rnd < 0.90 THEN
                -- Unplanned repair (non-breakdown)
                dt_start := rec_date;
                dt_end := rec_date + ((4 + floor(random() * 16))::int || ' hours')::interval;
                labor := (2500000 + (floor(random() * 3000000))::int);
                parts := (5000000 + (floor(random() * 8000000))::int);
                total := labor + parts;
                
                INSERT INTO maintenance_records (
                    id, truck_id, maintenance_type, service_date, odometer_reading,
                    vendor_name, invoice_number, labor_cost, parts_cost, total_cost,
                    total_cost_currency, is_unplanned, is_breakdown, downtime_start_at, downtime_end_at,
                    description, work_performed, performed_by_id, created_at
                ) VALUES (
                    gen_random_uuid(), t.id, 'Sửa chữa đột xuất', rec_date, COALESCE(t.current_odometer, 80000) - (10 - i) * 1000,
                    'Gara sửa chữa ô tô tải 24/7', 'INV-UNP-' || floor(random() * 100000)::text,
                    labor, parts, total, 'VND', TRUE, FALSE, dt_start, dt_end,
                    'Khắc phục sự cố rò rỉ khí nén và thay cảm biến ABS',
                    'Hàn ống xả, thay van chia khí nén, thay cảm biến ABS bánh sau', emp_id, rec_date
                );
            ELSE
                -- Roadside breakdown
                dt_start := rec_date;
                dt_end := rec_date + ((12 + floor(random() * 24))::int || ' hours')::interval;
                labor := (4000000 + (floor(random() * 4000000))::int);
                parts := (12000000 + (floor(random() * 15000000))::int);
                total := labor + parts;
                
                INSERT INTO maintenance_records (
                    id, truck_id, maintenance_type, service_date, odometer_reading,
                    vendor_name, invoice_number, labor_cost, parts_cost, total_cost,
                    total_cost_currency, is_unplanned, is_breakdown, downtime_start_at, downtime_end_at,
                    description, work_performed, performed_by_id, created_at
                ) VALUES (
                    gen_random_uuid(), t.id, 'Sự cố hỏng xe trên đường (Breakdown)', rec_date, COALESCE(t.current_odometer, 80000) - (10 - i) * 1000,
                    'Cứu hộ giao thông và sửa chữa lưu động', 'INV-BD-' || floor(random() * 100000)::text,
                    labor, parts, total, 'VND', TRUE, TRUE, dt_start, dt_end,
                    'Xe bị hỏng bơm cao áp / gãy láp trên cao tốc',
                    'Cứu hộ kéo xe về xưởng, thay bơm cao áp nhiên liệu và căn chỉnh kim phun', emp_id, rec_date
                );
            END IF;
        END LOOP;
    END LOOP;
END $$;
""")

    # 4. Load Condition Reports & Defects (for DIFOT)
    lines.append("""
-- 4. Load Condition Reports & Defects
DO $$
DECLARE
    l RECORD;
    emp_id uuid;
    report_id uuid;
    has_defect BOOLEAN;
BEGIN
    DELETE FROM condition_defects;
    DELETE FROM load_condition_reports;
    SELECT id INTO emp_id FROM employees LIMIT 1;
    
    FOR l IN SELECT id, delivered_at, requested_delivery_date FROM loads WHERE lower(status) = 'delivered' LOOP
        report_id := gen_random_uuid();
        
        -- Defect distribution: ~4% of on-time loads have a defect, ~10% of late loads have a defect
        IF l.delivered_at <= l.requested_delivery_date THEN
            has_defect := (random() < 0.04);
        ELSE
            has_defect := (random() < 0.10);
        END IF;
        
        INSERT INTO load_condition_reports (
            id, load_id, type, notes, inspected_at, inspected_by_id, created_at
        ) VALUES (
            report_id, l.id, 'delivery_inspection',
            CASE WHEN has_defect THEN 'Phát hiện bao bì hàng hóa bị móp méo nhẹ' ELSE 'Hàng hóa nguyên đai nguyên kiện, đủ số lượng' END,
            COALESCE(l.delivered_at, now()), emp_id, COALESCE(l.delivered_at, now())
        );
        
        IF has_defect THEN
            INSERT INTO condition_defects (
                id, load_condition_report_id, part_category, description, severity
            ) VALUES (
                gen_random_uuid(), report_id, 'packaging',
                'Thùng carton ngoài bị rách góc và móp góc pallet', 'minor'
            );
        END IF;
    END LOOP;
END $$;
""")

    # 5. Expenses
    lines.append("""
-- 5. Operating Expenses
DO $$
DECLARE
    t RECORD;
    exp_date timestamptz;
    emp_id uuid;
    i INT;
    fuel_amt numeric(18,2);
    labor_amt numeric(18,2);
    toll_amt numeric(18,2);
    maint_amt numeric(18,2);
BEGIN
    DELETE FROM expenses;
    SELECT id INTO emp_id FROM employees LIMIT 1;
    
    FOR t IN SELECT id FROM trucks LOOP
        -- For each month (April through Sept 2026), generate regular expenses
        FOR i IN 1..6 LOOP
            exp_date := '2026-03-25 00:00:00+00'::timestamptz + (i * 30 || ' days')::interval;
            
            -- Fuel Expense (~15,000,000 - 25,000,000 VND per month per truck)
            fuel_amt := (15000000 + (floor(random() * 10000000))::int);
            INSERT INTO expenses (
                id, type, status, vendor_name, expense_date, notes, amount_amount,
                amount_currency, truck_id, category, truck_expense_truck_id, truck_expense_category, created_at
            ) VALUES (
                gen_random_uuid(), 'fuel', 'approved', 'Tập đoàn Xăng dầu Petrolimex', exp_date,
                'Chi phí dầu Diesel vận tải đường dài', fuel_amt, 'VND', t.id, 'fuel', t.id, 'fuel', exp_date
            );
            
            -- Driver Labor Expense (~12,000,000 - 18,000,000 VND)
            labor_amt := (12000000 + (floor(random() * 6000000))::int);
            INSERT INTO expenses (
                id, type, status, vendor_name, expense_date, notes, amount_amount,
                amount_currency, truck_id, category, truck_expense_truck_id, truck_expense_category, created_at
            ) VALUES (
                gen_random_uuid(), 'driver_labor', 'approved', 'Lương tài xế chuyến', exp_date,
                'Lương và phụ cấp tài xế đường dài', labor_amt, 'VND', t.id, 'driverLabor', t.id, 'driverLabor', exp_date
            );
            
            -- Tolls Expense (~3,000,000 - 6,000,000 VND)
            toll_amt := (3000000 + (floor(random() * 3000000))::int);
            INSERT INTO expenses (
                id, type, status, vendor_name, expense_date, notes, amount_amount,
                amount_currency, truck_id, category, truck_expense_truck_id, truck_expense_category, created_at
            ) VALUES (
                gen_random_uuid(), 'tolls', 'approved', 'VETC / ePass Thu phí tự động', exp_date,
                'Phí BOT đường bộ cao tốc', toll_amt, 'VND', t.id, 'tolls', t.id, 'tolls', exp_date
            );
            
            -- Maintenance Expense (~2,000,000 - 5,000,000 VND)
            maint_amt := (2000000 + (floor(random() * 3000000))::int);
            INSERT INTO expenses (
                id, type, status, vendor_name, expense_date, notes, amount_amount,
                amount_currency, truck_id, category, truck_expense_truck_id, truck_expense_category, created_at
            ) VALUES (
                gen_random_uuid(), 'maintenance', 'approved', 'Phụ tùng và vật tư sửa chữa', exp_date,
                'Chi phí bảo dưỡng định kỳ và thay thế phụ tùng hao mòn', maint_amt, 'VND', t.id, 'maintenance', t.id, 'maintenance', exp_date
            );
        END LOOP;
    END LOOP;
END $$;
""")

    lines.append("COMMIT;")
    return "\n".join(lines)

if __name__ == "__main__":
    sql = generate_sql()
    with open("/tmp/seed_operational_data.sql", "w", encoding="utf-8") as f:
        f.write(sql)
    print("Generated /tmp/seed_operational_data.sql successfully.")
