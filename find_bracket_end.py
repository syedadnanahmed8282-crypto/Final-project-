with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

lines = content.split('\n')
start = 2811
bracket_count = 0
for idx in range(start-1, len(lines)):
    line = lines[idx]
    if idx == start-1:
        # the first line
        bracket_count += line.count('{')
        bracket_count -= line.count('}')
        print(f"Line {idx+1}: {line}")
    else:
        bracket_count += line.count('{')
        bracket_count -= line.count('}')
        if bracket_count == 0:
            print(f"Ends on Line {idx+1}: {line}")
            break
