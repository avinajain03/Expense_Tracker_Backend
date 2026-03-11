# Dual GitHub Account — One Machine, Two Repos, Two Authors

A step-by-step guide to push the same project to two different GitHub repos from one machine, with each repo showing its own owner as the commit author.

---

## Prerequisites

- Git installed on your machine
- Two GitHub accounts (Account A and Account B)
- Both repos already created on GitHub (empty, no README)

---

## Step 1: Generate SSH Keys

Open **PowerShell** and generate a separate SSH key for each account.

```powershell
# Key for Account A
ssh-keygen -t ed25519 -C "accountA@email.com" -f "$env:USERPROFILE\.ssh\id_accountA" -N '""'

# Key for Account B
ssh-keygen -t ed25519 -C "accountB@email.com" -f "$env:USERPROFILE\.ssh\id_accountB" -N '""'
```

> If you already have an existing key (e.g. `id_ed25519`), you can reuse it for Account A and only generate a new key for Account B.

---

## Step 2: Add Public Keys to GitHub

Get each public key:

```powershell
# Account A's key
Get-Content "$env:USERPROFILE\.ssh\id_accountA.pub"

# Account B's key — copy to a text file to avoid truncation
Copy-Item "$env:USERPROFILE\.ssh\id_accountB.pub" -Destination ".\accountB_key.txt"
```

For each account:
1. Log in to GitHub as that account
2. Go to **Settings → SSH and GPG Keys → New SSH Key**
3. Paste the key → Save

---

## Step 3: Configure `~/.ssh/config`

Create or edit `C:\Users\<you>\.ssh\config`:

```
# Account A
Host github-accountA
    HostName github.com
    User git
    IdentityFile ~/.ssh/id_accountA

# Account B
Host github-accountB
    HostName github.com
    User git
    IdentityFile ~/.ssh/id_accountB
```

> The `Host` aliases (`github-accountA`, `github-accountB`) are used in your remote URLs instead of `github.com`.

---

## Step 4: Verify SSH Connections

```powershell
ssh -T git@github-accountA
# Expected: Hi AccountA! You've successfully authenticated...

ssh -T git@github-accountB
# Expected: Hi AccountB! You've successfully authenticated...
```

---

## Step 5: Initialize Local Project

```powershell
mkdir MyProject
cd MyProject
git init

# Set git identity to Account A (the machine's primary account)
git config user.name "Account A Name"
git config user.email "accountA@email.com"
```

---

## Step 6: Add Both Remotes

Use the SSH host aliases from Step 3:

```powershell
git remote add origin-a git@github-accountA:AccountA/MyProject.git
git remote add origin-b git@github-accountB:AccountB/MyProject.git

# Verify
git remote -v
```

---

## Step 7: Create the Push Script

Create a file called `push-both.ps1` in your project root:

```powershell
# push-both.ps1
# Pushes commits to both repos with the correct author for each.
# Usage: powershell -ExecutionPolicy Bypass -File ".\push-both.ps1"

$BRANCH = "master"
$ACCOUNT_B_NAME = "Account B Name"
$ACCOUNT_B_EMAIL = "accountB@email.com"
$TEMP_BRANCH = "accountB-push-temp"

Write-Host "`n>>> Pushing to Account A's repo (origin-a)..." -ForegroundColor Cyan
git push origin-a $BRANCH
if ($LASTEXITCODE -ne 0) {
    Write-Host "Push to origin-a failed." -ForegroundColor Red; exit 1
}
Write-Host "Done." -ForegroundColor Green

Write-Host "`n>>> Rewriting author + committer as Account B..." -ForegroundColor Cyan
git checkout -b $TEMP_BRANCH
$env:GIT_COMMITTER_NAME = $ACCOUNT_B_NAME
$env:GIT_COMMITTER_EMAIL = $ACCOUNT_B_EMAIL
git commit --amend --author="$ACCOUNT_B_NAME <$ACCOUNT_B_EMAIL>" --no-edit
Remove-Item Env:GIT_COMMITTER_NAME
Remove-Item Env:GIT_COMMITTER_EMAIL

Write-Host "`n>>> Pushing to Account B's repo (origin-b)..." -ForegroundColor Cyan
git push --force origin-b "$TEMP_BRANCH`:$BRANCH"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Push to origin-b failed." -ForegroundColor Red
    git checkout $BRANCH; git branch -D $TEMP_BRANCH; exit 1
}
Write-Host "Done." -ForegroundColor Green

Write-Host "`n>>> Cleaning up..." -ForegroundColor Cyan
git checkout $BRANCH
git branch -D $TEMP_BRANCH

Write-Host "`nAll done! Both repos are in sync." -ForegroundColor Green
Write-Host "  AccountA/MyProject --> author: Account A"
Write-Host "  AccountB/MyProject --> author: Account B"
```

> **Customize**: Replace `Account B Name`, `accountB@email.com`, `origin-a`, `origin-b` with your actual values.

---

## Step 8: First Commit & Push

```powershell
# Create a file and make first commit
echo "# My Project" > README.md
git add .
git commit -m "Initial commit"

# Push to both repos
powershell -ExecutionPolicy Bypass -File ".\push-both.ps1"
```

---

## Daily Workflow

```powershell
# 1. Make your changes
git add .
git commit -m "your commit message"

# 2. Push to both repos with correct authors
powershell -ExecutionPolicy Bypass -File ".\push-both.ps1"
```

That's it! Each repo will show its own account as the commit author. ✅

---

## How It Works

| What | Account A's Repo | Account B's Repo |
|------|-----------------|-----------------|
| Code | ✅ Identical | ✅ Identical |
| Commit message | ✅ Same | ✅ Same |
| Author | `Account A` | `Account B` |
| Committer | `Account A` | `Account B` |
| Commit hash | Original | Rewritten (different) |

> The two repos will have **different commit hashes** but **identical code and messages**. This is expected and unavoidable when rewriting author identity.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Permission denied (publickey)` | SSH key not added to GitHub. Re-check Step 2. |
| `Get-Content` not recognized | You're in CMD, not PowerShell. Use `type %USERPROFILE%\.ssh\key.pub` instead |
| Key rejected by GitHub ("invalid format") | Key was truncated. Copy to a `.txt` file and open in Notepad to copy cleanly |
| GitHub shows "authored and committed" by different people | `GIT_COMMITTER_*` env vars not set — make sure you're using the updated script from Step 7 |