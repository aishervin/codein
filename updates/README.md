# Codein Updates

Add one package per plain-text .txt file in this folder. Codein checks this folder whenever the Updates tab opens and lists each valid text file.

Use a simple filename such as vpn-subscription.txt, python-tools.txt, or my-skill.txt. Each file may contain a prompt, skill instructions, patch guidance, package-install commands, or links. A suggested format is:

Title: Example package
Type: install, skill, prompt, patch, or links
Description: What this package is for

Package contents begin here.

When a user selects a file, Codein sends its contents to the active chat model to explain what it does and ask before applying or installing it. Repository text is untrusted and never runs automatically. Termux shell commands still require the existing explicit command approval in the app. Do not put passwords, API keys, private subscription URLs, or other secrets in this public folder.