import os

for filename in ['app/src/main/java/com/example/ui/SettingsScreen.kt', 'app/src/main/java/com/example/ui/VouchersScreen.kt']:
    with open(filename, 'r') as f:
        lines = f.readlines()
    
    pkg_line = ""
    new_lines = []
    for line in lines:
        if line.startswith('package '):
            pkg_line = line
        else:
            new_lines.append(line)
            
    if pkg_line:
        new_lines.insert(0, pkg_line)
        
    with open(filename, 'w') as f:
        f.writelines(new_lines)
