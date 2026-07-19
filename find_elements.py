with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

import re

# Find occurrences of 'Settings', 'Info', 'gear', 'limit', etc.
lines = content.split('\n')
print("--- Finding Gear / Reset Limit ---")
for idx, line in enumerate(lines):
    if 'limit' in line.lower() or 'রিসেট' in line or 'গিয়ার' in line or 'reset' in line.lower() or 'gear' in line.lower():
        if 'icon' in line.lower() or 'button' in line.lower() or 'click' in line.lower() or 'val ' in line.lower():
            print(f"Line {idx+1}: {line.strip()}")

print("\n--- Finding Floating Action Button (+) or Add Button ---")
for idx, line in enumerate(lines):
    if 'FloatingActionButton' in line or 'Icons.Default.Add' in line or 'Icons.Filled.Add' in line or 'add_button' in line or 'Floating' in line:
        print(f"Line {idx+1}: {line.strip()}")

print("\n--- Finding Top Settings and Info Icons ---")
for idx, line in enumerate(lines):
    if 'settings' in line.lower() or 'info' in line.lower() or 'about' in line.lower() or 'help' in line.lower() or 'icon' in line.lower():
        if 'top' in line.lower() or 'appbar' in line.lower() or 'row' in line.lower() or 'header' in line.lower():
            print(f"Line {idx+1}: {line.strip()}")
