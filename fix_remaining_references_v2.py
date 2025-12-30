import os

replacements = {
    # Layout IDs in Bindings
    "onboardingDaxDialogBackground": "onboardingRevengeDialogBackground",
    "onboardingDaxDialogContainer": "onboardingRevengeDialogContainer",
    
    # Correcting mistakes in previous runs
    "fireClearAllPlusRevengeChats": "fireClearAllPlusRevenge_Chats",
    "fireClearAllPlusDuckChats": "fireClearAllPlusRevenge_Chats",
}

def apply_replacements(directory):
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.endswith(".kt") or file.endswith(".java") or file.endswith(".xml"):
                path = os.path.join(root, file)
                with open(path, 'r') as f:
                    content = f.read()
                
                new_content = content
                for old, new in replacements.items():
                    new_content = new_content.replace(old, new)
                
                if new_content != content:
                    print(f"Updating {path}")
                    with open(path, 'w') as f:
                        f.write(new_content)

apply_replacements("app/src/main")
apply_replacements("duckchat/duckchat-impl/src/main")
