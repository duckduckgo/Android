# GitHub Secrets Setup Guide

This document describes the secrets required for the `pixel-validation` workflow to function properly.

## Required Secret: `DAXMOBILE_ANDROID_AUTOMATION`

### Purpose
This secret provides authentication to checkout the private repository `duckduckgo/internal-github-asana-utils`, which contains the `user_map.yml` file needed for pixel validation.

### How to Create

#### Step 1: Generate a Personal Access Token (PAT)

1. Go to GitHub Settings → [Developer settings → Personal access tokens → Fine-grained tokens](https://github.com/settings/tokens?type=beta)
2. Click **Generate new token**
3. Configure the token with:
   - **Token name**: `daxmobile-android-automation`
   - **Expiration**: 90 days (or your preferred duration)
   - **Repository access**: Only select repositories
     - Select: `duckduckgo/internal-github-asana-utils`
   - **Permissions**:
     - Repository permissions → Contents: `Read-only`
4. Click **Generate token**
5. **Copy the token immediately** (you won't be able to see it again)

#### Step 2: Add Secret to Repository

1. Go to your repository: https://github.com/daotuananh1999tgdd-sudo/Android
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Configure:
   - **Name**: `DAXMOBILE_ANDROID_AUTOMATION`
   - **Secret**: Paste the token you copied in Step 1
5. Click **Add secret**

#### Step 3: Verify

1. Navigate to the **Actions** tab in your repository
2. Select the **Pixel Schema Validation** workflow
3. Click **Run workflow** → **Run workflow**
4. The workflow should now complete successfully with access to the private repository

---

## Security Notes

- ✅ The token is **read-only** and scoped to a single private repository
- ✅ Fine-grained PATs are more secure than classic PATs (narrower scope)
- ✅ Set an expiration date and rotate regularly
- ⚠️ Never commit the token to version control
- ⚠️ The secret is only available to workflows on the default branch and protected branches

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Token expired | Generate a new fine-grained PAT and update the secret |
| "Input required and not supplied: token" | Verify the secret exists in Settings → Secrets and variables → Actions |
| Permission denied when accessing repo | Ensure the token has `contents: read` permission for `duckduckgo/internal-github-asana-utils` |
| Forked PR still fails | This is expected — forked PRs don't have access to repository secrets for security reasons. The workflow has been updated to gracefully degrade in this case. |

---

## Workflow Behavior

### When secret is available (push to develop or same-repo PR):
- ✅ Checks out `duckduckgo/internal-github-asana-utils`
- ✅ Validates with `user_map.yml`
- ✅ Full validation enabled

### When secret is unavailable (forked PR):
- ✅ Skips private repo checkout
- ✅ Validates without `user_map.yml`
- ✅ Workflow completes successfully

---

**Questions?** Check the [GitHub PAT documentation](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) or review the workflow file at `.github/workflows/pixel-validation.yml`.
