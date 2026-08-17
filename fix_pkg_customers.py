with open('app/src/main/java/com/example/ui/CustomersScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
pkg_line = ""
for line in lines:
    if line.startswith("package "):
        pkg_line = line
    else:
        new_lines.append(line)

if pkg_line:
    new_lines.insert(0, pkg_line)

with open('app/src/main/java/com/example/ui/CustomersScreen.kt', 'w') as f:
    f.writelines(new_lines)
