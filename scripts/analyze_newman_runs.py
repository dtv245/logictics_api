import json

def analyze_run(report_path, name):
    with open(report_path) as f:
        rep = json.load(f)

    folder_stats = {}
    skipped_list = []

    for exec_item in rep['run']['executions']:
        folder = "Unknown"
        # Determine folder from item parent or name
        item_name = exec_item['item']['name']
        prefix = item_name.split('.')[0].strip() if '.' in item_name else ""
        
        # Mapping prefix to folder name
        prefix_folder_map = {
            "00": "00_Health",
            "01": "01_Auth",
            "02": "02_MasterData",
            "03": "03_PayrollConfig",
            "04": "04_RatingSetup",
            "05": "05_Load",
            "06": "06_RatingFlow",
            "07": "07_TripPlanning",
            "08": "08_Optimization",
            "09": "09_FleetHistory",
            "10": "10_TripExecution",
            "11": "11_Costs",
            "12": "12_Billing",
            "13": "13_DriverSettlement",
            "14": "14_PayrollRun",
            "15": "15_Messaging_Notifications",
            "16": "16_Reports",
            "17": "17_LarkBase",
            "18": "18_Documents",
            "19": "19_Negative_Security",
            "99": "99_Cleanup"
        }
        folder = prefix_folder_map.get(prefix, "Other")

        if folder not in folder_stats:
            folder_stats[folder] = {"total": 0, "pass": 0, "fail": 0}

        folder_stats[folder]["total"] += 1
        
        # Check assertions
        has_fail = False
        assertions = exec_item.get('assertions', [])
        for a in assertions:
            if a.get('error'):
                has_fail = True
                break

        if has_fail:
            folder_stats[folder]["fail"] += 1
        else:
            folder_stats[folder]["pass"] += 1

    # Extract skips from skipped_executions if available or from difference with collection
    with open("postman/LogisticsX.postman_collection.json") as f:
        col = json.load(f)

    def get_col_items(items, folder=''):
        res = []
        for it in items:
            if 'item' in it:
                res.extend(get_col_items(it['item'], it.get('name', folder)))
            elif 'request' in it:
                res.append((it.get('name', ''), folder))
        return res

    all_col_items = get_col_items(col['item'])
    executed_names = set(e['item']['name'] for e in rep['run']['executions'])

    for name_it, f_it in all_col_items:
        if name_it not in executed_names:
            skipped_list.append((f_it, name_it))

    print(f"================================================================================")
    print(f"ANALYSIS REPORT FOR {name}")
    print(f"================================================================================")
    print(f"{'Folder':<30} | {'Executed':<8} | {'Passed':<8} | {'Failed':<8} | {'Skipped':<8}")
    print("-" * 74)

    total_exec = 0
    total_pass = 0
    total_fail = 0

    # Group skips by folder
    skips_by_folder = {}
    for f_it, n_it in skipped_list:
        skips_by_folder[f_it] = skips_by_folder.get(f_it, 0) + 1

    # All folders
    all_f_names = sorted(set(list(folder_stats.keys()) + list(skips_by_folder.keys())))
    for f in all_f_names:
        stats = folder_stats.get(f, {"total": 0, "pass": 0, "fail": 0})
        sk = skips_by_folder.get(f, 0)
        total_exec += stats["total"]
        total_pass += stats["pass"]
        total_fail += stats["fail"]
        print(f"{f:<30} | {stats['total']:<8} | {stats['pass']:<8} | {stats['fail']:<8} | {sk:<8}")

    print("-" * 74)
    print(f"{'TOTAL':<30} | {total_exec:<8} | {total_pass:<8} | {total_fail:<8} | {len(skipped_list):<8}")
    print(f"\nSkipped Requests in this run ({len(skipped_list)} requests):")
    for f_it, n_it in skipped_list:
        print(f"  - [{f_it}] {n_it}")
    print("\n")

analyze_run("postman/runs/run1_report.json", "RUN 1 (CONSECUTIVE RUN #1)")
analyze_run("postman/runs/run2_report.json", "RUN 2 (CONSECUTIVE RUN #2)")
analyze_run("postman/runs/run3_report.json", "RUN 3 (RUN WITH runCleanup=true)")
