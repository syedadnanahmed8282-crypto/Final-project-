with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

lines = content.split('\n')

print("--- LINES around 1359 (Gear/Reset Limit) ---")
for i in range(1340, 1380):
    if i < len(lines):
        print(f"{i+1}: {lines[i]}")

print("\n--- FINDING 'i' info icon or main settings icon ---")
# Usually top app bar has settings icon or info icon. Let's find "Info" or "info" or "Settings" or "settings" icons.
for idx, line in enumerate(lines):
    if 'Icons.Default.Info' in line or 'Icons.Default.Settings' in line or 'Icons.Outlined.Info' in line or 'Icons.Outlined.Settings' in line or 'Icons.Filled.Settings' in line or 'Icons.Filled.Info' in line or 'Icons.Default.Help' in line:
        print(f"Line {idx+1}: {line.strip()}")
        # print some context
        for offset in range(-5, 6):
            c_idx = idx + offset
            if 0 <= c_idx < len(lines):
                print(f"  {c_idx+1}: {lines[c_idx]}")
