# Hướng dẫn Setup GitHub Repository & CI/CD

## 📋 Mục lục
1. [Tạo GitHub Repository](#1-tạo-github-repository)
2. [Upload code lên GitHub](#2-upload-code-lên-github)
3. [Cấu hình GitHub Actions](#3-cấu-hình-github-actions)
4. [Tạo Release tự động](#4-tạo-release-tự-động)
5. [Sử dụng CI/CD](#5-sử-dụng-cicd)

---

## 1. Tạo GitHub Repository

### Bước 1.1: Tạo repository mới

1. Truy cập https://github.com/new
2. Điền thông tin:
   - **Repository name**: `r1-xiaozhi` (hoặc tên bạn muốn)
   - **Description**: `Xiaozhi Voice Assistant for Phicomm R1 - AI-powered smart speaker`
   - **Visibility**: 
     - ✅ **Public** (khuyến nghị - để community contribute)
     - ⚪ **Private** (nếu muốn giữ riêng tư)
3. **KHÔNG** chọn:
   - ❌ Add a README file
   - ❌ Add .gitignore
   - ❌ Choose a license
4. Click **"Create repository"**

### Bước 1.2: Lưu URL repository

Bạn sẽ thấy URL kiểu:
```
https://github.com/YOUR_USERNAME/r1-xiaozhi.git
```

---

## 2. Upload code lên GitHub

### Bước 2.1: Initialize Git (nếu chưa có)

Mở terminal/command prompt tại thư mục dự án (`f:/PHICOMM_R1/xiaozhi/r1xiaozhi`):

```bash
# Initialize git repository
git init

# Add all files
git add .

# Commit
git commit -m "Initial commit: Xiaozhi Voice Assistant for Phicomm R1"
```

### Bước 2.2: Connect to GitHub

```bash
# Add remote repository (thay YOUR_USERNAME bằng username GitHub của bạn)
git remote add origin https://github.com/YOUR_USERNAME/r1-xiaozhi.git

# Verify remote
git remote -v
```

### Bước 2.3: Push code

```bash
# Push to GitHub
git branch -M main
git push -u origin main
```

**Lưu ý**: Nếu được hỏi authentication, sử dụng:
- **Username**: GitHub username của bạn
- **Password**: Personal Access Token (PAT)
  - Tạo PAT tại: https://github.com/settings/tokens
  - Chọn scopes: `repo`, `workflow`

---

## 3. Cấu hình GitHub Actions

GitHub Actions đã được cấu hình sẵn! Bạn có 2 workflows:

### Workflow 1: Build APK (`.github/workflows/build.yml`)

**Tự động chạy khi:**
- ✅ Push code lên branch `main`, `master`, hoặc `develop`
- ✅ Tạo Pull Request
- ✅ Tạo tag `v*` (ví dụ: `v1.0.0`)
- ✅ Manual trigger (qua giao diện GitHub)

**Outputs:**
- Debug APK (retention: 30 days)
- Release APK (retention: 90 days)
- SHA256 checksums

### Workflow 2: Code Quality (`.github/workflows/test.yml`)

**Tự động chạy khi:**
- ✅ Push code lên branch `main`, `master`, `develop`
- ✅ Tạo Pull Request

**Checks:**
- Lint checks
- Unit tests
- Code analysis

### Bước 3.1: Verify workflows

1. Truy cập: `https://github.com/YOUR_USERNAME/r1-xiaozhi/actions`
2. Bạn sẽ thấy workflows đang chạy
3. Click vào workflow để xem progress

### Bước 3.2: Download APK từ Actions

Sau khi build thành công:

1. Vào **Actions** tab
2. Click vào workflow run mới nhất
3. Scroll xuống **Artifacts** section
4. Download:
   - `R1Xiaozhi-Debug-APK`
   - `R1Xiaozhi-Release-APK`
   - `Checksums`

---

## 4. Tạo Release tự động

### Bước 4.1: Tạo tag để trigger release

```bash
# Create and push a version tag
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

### Bước 4.2: GitHub tự động:

1. ✅ Build Release APK
2. ✅ Generate checksums
3. ✅ Create GitHub Release
4. ✅ Upload APK và checksums as release assets
5. ✅ Generate release notes

### Bước 4.3: Xem Release

Truy cập: `https://github.com/YOUR_USERNAME/r1-xiaozhi/releases`

Release sẽ có:
- 📦 Release APK file
- 🔐 SHA256 checksums
- 📝 Release notes với changelog
- 📥 Download instructions

---

## 5. Sử dụng CI/CD

### 5.1. Workflow cơ bản

**Khi develop:**
```bash
# 1. Make changes
git add .
git commit -m "feat: add new feature"

# 2. Push to trigger CI
git push origin main

# 3. GitHub Actions tự động:
#    - Build APK
#    - Run tests
#    - Upload artifacts
```

**Khi release:**
```bash
# 1. Update version in build.gradle
# Mở R1XiaozhiApp/app/build.gradle
# Tăng versionCode và versionName

# 2. Commit changes
git add R1XiaozhiApp/app/build.gradle
git commit -m "chore: bump version to 1.0.1"
git push

# 3. Create tag
git tag -a v1.0.1 -m "Release version 1.0.1"
git push origin v1.0.1

# 4. GitHub tự động tạo Release với APK
```

### 5.2. Manual trigger build

Nếu muốn build thủ công:

1. Vào **Actions** tab
2. Chọn **"Build Android APK"** workflow
3. Click **"Run workflow"** button
4. Chọn branch
5. Click **"Run workflow"**

### 5.3. Badges cho README

Thêm build status badges vào README:

```markdown
[![Build](https://github.com/YOUR_USERNAME/r1-xiaozhi/actions/workflows/build.yml/badge.svg)](https://github.com/YOUR_USERNAME/r1-xiaozhi/actions/workflows/build.yml)
[![Tests](https://github.com/YOUR_USERNAME/r1-xiaozhi/actions/workflows/test.yml/badge.svg)](https://github.com/YOUR_USERNAME/r1-xiaozhi/actions/workflows/test.yml)
```

---

## 6. Advanced: Signing APK (Optional)

Để sign release APK với keystore của bạn:

### Bước 6.1: Tạo keystore

```bash
keytool -genkey -v -keystore r1xiaozhi.keystore -alias r1xiaozhi -keyalg RSA -keysize 2048 -validity 10000
```

### Bước 6.2: Add secrets to GitHub

1. Vào **Settings** → **Secrets and variables** → **Actions**
2. Click **"New repository secret"**
3. Thêm:
   - `KEYSTORE_FILE`: Base64 của keystore file
   - `KEYSTORE_PASSWORD`: Password của keystore
   - `KEY_ALIAS`: Alias của key
   - `KEY_PASSWORD`: Password của key

```bash
# Convert keystore to base64
base64 r1xiaozhi.keystore > keystore.txt
# Copy nội dung keystore.txt và paste vào KEYSTORE_FILE
```

### Bước 6.3: Update build.yml

Uncomment phần signing trong `.github/workflows/build.yml`

---

## 7. Troubleshooting

### Build failed: "Permission denied"

```bash
# Make gradlew executable
git update-index --chmod=+x R1XiaozhiApp/gradlew
git commit -m "fix: make gradlew executable"
git push
```

### Build failed: "SDK not found"

GitHub Actions tự động setup SDK. Nếu vẫn lỗi:
- Check JDK version trong workflow (phải là JDK 8)
- Check Android SDK version trong build.gradle

### Artifacts không tải được

- Check retention days (mặc định 30 days cho debug, 90 days cho release)
- Artifacts tự động xóa sau thời gian retention

### Release không tự động tạo

- Verify tag format: phải là `v*` (ví dụ: `v1.0.0`)
- Check permissions: `GITHUB_TOKEN` phải có write access

---

## 8. Best Practices

### Versioning

Sử dụng Semantic Versioning:
- `v1.0.0` - Major release
- `v1.1.0` - Minor update (new features)
- `v1.1.1` - Patch (bug fixes)

### Branch Strategy

```
main/master     ← Production-ready code
  ↑
develop         ← Development branch
  ↑
feature/*       ← Feature branches
```

### Commit Messages

```bash
feat: Add new feature
fix: Fix bug
docs: Update documentation
chore: Update dependencies
refactor: Refactor code
test: Add tests
```

---

## 9. Monitoring

### Check build status

```bash
# Via GitHub CLI
gh run list

# View specific run
gh run view RUN_ID
```

### Download artifacts via CLI

```bash
# List artifacts
gh run download RUN_ID --list

# Download specific artifact
gh run download RUN_ID -n R1Xiaozhi-Release-APK
```

---

## 🎉 Done!

Bây giờ repository của bạn đã có:
- ✅ Automated builds on every push
- ✅ Automatic releases on tags
- ✅ Code quality checks
- ✅ APK artifacts available for download
- ✅ Professional CI/CD pipeline

**Next steps:**
1. Share repository với community
2. Add contributors
3. Accept Pull Requests
4. Keep building awesome features!

---

**Repository URL example:**
```
https://github.com/YOUR_USERNAME/r1-xiaozhi
```

**Download latest release:**
```
https://github.com/YOUR_USERNAME/r1-xiaozhi/releases/latest