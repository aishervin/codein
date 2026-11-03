# SHΞN™Termutoxic (شن)

**SHΞN™Termutoxic** یک عامل هوشمند، خودکار و سخنگو برای مدیریت کامل محیط Termux روی دستگاه اندروید شماست.

---

## 🇮🇷 راهنمای فارسی (Persian Guide)

### ⚡ ویژگی‌های اصلی
- **چت طبیعی و هوشمند به زبان فارسی**: «پایتون و نودجی‌اس رو نصب کن و یه وب‌سرور بالا بیار»
- **اتصال دوکاناله به ترموکس**: SSH مستقیم روی پورت `8022` + پشتیبانی جانبی از `RUN_COMMAND` Intent
- **گاوصندوق امنیتی (Integrations Vault)**: نگهداری رمزنگاری‌شده توکن‌های GitHub و Cloudflare با رمزنگاری Android Keystore + AES-256-GCM
- **بازگردانی اسنپ‌شات (One-Tap Undo)**: ایجاد اسنپ‌شات خودکار قبل از ویرایش یا حذف فایل‌ها
- **داشبورد پروسه‌ها و پورت‌ها**: مشاهده پروسه‌های فعال، پورت‌های باز، تانل‌های کلودفلر و سرورهای MCP
- **ویرایشگر دستورالعمل سیستم (System Prompt)**: سفارشی‌سازی کامل رفتار عامل

---

### 🚀 نحوه راه اندازی در ترموکس
۱. برنامه **SHΞN™Termutoxic** را باز کنید.
۲. به زبانه **«راه اندازی» (Setup)** بروید.
۳. دستور تک‌خطی بوتاسترپ را کپی کرده و در Termux اجرا کنید:
```bash
pkg update -y && pkg install -y openssh curl && mkdir -p ~/.ssh ~/.termux ~/.config/shen && chmod 700 ~/.ssh && echo "PUBLIC_KEY_HERE" >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && echo "allow-external-apps=true" >> ~/.termux/termux.properties && termux-reload-settings && pkill sshd || true && sshd -p 8022 && echo "SHEN_BOOTSTRAP_SUCCESS_8022"
```
۴. روی دکمه **«تست اتصال»** بزنید. وضعیت باید به `✅ متصل شد (SSH 8022)` تغییر کند.

---

### 🔑 ساخت فایل KeyStore برای GitHub Actions
جهت ساخت APK امضاشده توسط GitHub Actions، از دستور زیر استفاده کنید:

```bash
keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
base64 -w 0 release-key.jks > keystore_base64.txt
```

سپس مقادیر زیر را در **GitHub Secrets** مخزن خود قرار دهید:
- `KEYSTORE_BASE64`: محتوای فایل `keystore_base64.txt`
- `KEYSTORE_PASSWORD`: رمز کی‌استور
- `KEY_ALIAS`: `upload`
- `KEY_PASSWORD`: رمز کلید

---

## 🇬🇧 English Guide

### Features
- **Conversational Natural Language Agent**: Controls your own Termux environment using Persian or English.
- **Dual Connection Channel**: Primary local SSH on `127.0.0.1:8022` + Termux `RUN_COMMAND` intent fallback.
- **Integrations Vault**: Encrypted storage for GitHub PAT & Cloudflare API tokens via Android Keystore.
- **Snapshot Protection**: Automatic pre-write/pre-delete file snapshots with 1-tap restore.
- **GitHub Actions CI/CD**: Automated signed APK release builds.

---

### GitHub Actions Secrets Setup
Configure the following Repository Secrets in GitHub (`Settings -> Secrets and variables -> Actions`):
1. `KEYSTORE_BASE64`: Output of `base64 -w 0 release-key.jks`
2. `KEYSTORE_PASSWORD`: Keystore password
3. `KEY_ALIAS`: `upload`
4. `KEY_PASSWORD`: Key password
