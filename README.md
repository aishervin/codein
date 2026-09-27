<div dir="rtl" align="center">
  <img src="https://raw.githubusercontent.com/aishervin/codein/main/app/src/main/res/drawable/shen_logo.png" alt="CODΞiN logo" width="112" />
  <h1>CODΞiN™</h1>
  <p><strong>SHΞN™ Coder</strong></p>
  <p>یک تجربه‌ی متمرکز، سریع و خوش‌ساخت برای گفتگو، ایده‌پردازی و کار با کد در اندروید.</p>
  <p dir="ltr">
    <a href="https://github.com/aishervin/codein/releases"><img src="https://img.shields.io/github/v/release/aishervin/codein?display_name=tag&style=flat-square&color=ff7a00" alt="Latest release" /></a>
    <a href="https://github.com/aishervin/codein/actions/workflows/android-release.yml"><img src="https://img.shields.io/github/actions/workflow/status/aishervin/codein/android-release.yml?style=flat-square&label=build" alt="Build status" /></a>
    <img src="https://img.shields.io/badge/Android-7.0%2B-3ddc84?style=flat-square" alt="Android 7.0+" />
    <img src="https://img.shields.io/badge/Kotlin-Compose-7f52ff?style=flat-square" alt="Kotlin Compose" />
  </p>
</div>

<br />

## معرفی

**Codein** یک کلاینت اندرویدی مستقل با هویت بصری **SHΞN™** است؛ جایی برای تبدیل سؤال، ایده و مسئله‌ی فنی به یک گفتگوی روشن و قابل‌استفاده.

هدف پروژه ساده است: حذف شلوغی‌های اضافه، کوتاه‌کردن مسیر رسیدن به پاسخ و ساختن محیطی که برای فکرکردن و نوشتن کد حس خوبی داشته باشد.

## امکانات فعلی

### ⚡ گفتگوی روان و متمرکز

پاسخ‌ها به‌صورت زنده نمایش داده می‌شوند تا گفتگو طبیعی‌تر باشد و لازم نباشد برای دیدن نتیجه منتظر پایان کامل پاسخ بمانید.

### ◈ presetهای مدل

بین presetهای **ZERO** و **PRO** جابه‌جا شوید؛ با لمس یا حرکت عمودی روی کنترل مدل، بدون خروج از گفتگو حالت موردنظر را انتخاب کنید.

### ⌘ ابزارهای کدنویسی

- نمایش خوانای بلوک‌های کد با رنگ‌بندی سبک و قاب نارنجی Codein
- کپی سریع متن یا کد
- دانلود کد با نام فایل مناسب زبان
- تلاش دوباره برای پیام‌های قبلی
- پشتیبانی از پیوست‌های متنی تا حجم ۱ مگابایت

### ◉ رابط کاربری اختصاصی

تم تیره‌ی neo-morphic، خطوط نارنجی ظریف، لوگوی متحرک، وضعیت اتصال زنده و تایپوگرافی اختصاصی، هویت Codein را از یک صفحه‌ی ساده‌ی گفتگو جدا می‌کند.

### ⚙ کنترل جلسه

از بخش Settings می‌توانید گفتگوی تازه شروع کنید، داده‌های جلسه‌ی مرورگر را پاک کنید و وضعیت اتصال سرویس را ببینید.

## نصب

آخرین نسخه را از بخش [Releases](https://github.com/aishervin/codein/releases) دریافت کنید.

Codein برای **Android 7.0 یا بالاتر** ساخته شده است. چون APK خارج از Google Play توزیع می‌شود، ممکن است Android هنگام نصب یا اجرای نخست یک هشدار امنیتی نمایش دهد. فایل را فقط از release رسمی همین مخزن دریافت کنید.

## شروع سریع

1. برنامه را باز کنید و منتظر نمایش وضعیت `RUN` بمانید.
2. روی composer پایین صفحه بزنید و پیام خود را بنویسید.
3. برای ارسال، دکمه‌ی فلش را لمس کنید.
4. برای انتخاب مدل یا مدیریت جلسه، از کنترل بالای صفحه و Settings استفاده کنید.

## معماری پروژه

Codein با **Kotlin** و **Jetpack Compose** ساخته شده است. رابط کاربری در Compose اجرا می‌شود و یک bridge سبک، ارتباط جلسه‌ی گفتگو و پاسخ‌های streaming را به‌صورت غیرقابل‌مشاهده مدیریت می‌کند.

جزئیات release و روند ساخت APK امضاشده در [`docs/release.md`](docs/release.md) قرار دارد.

## ساخت از سورس

پیش‌نیازها:

- JDK 17
- Android SDK 36
- Gradle wrapper موجود در مخزن

برای ساخت نسخه‌ی debug:

```bash
bash ./gradlew assembleDebug
```

برای ساخت نسخه‌ی release امضاشده، راهنمای [`docs/release.md`](docs/release.md) را دنبال کنید. کلید signing پایدار باید حفظ شود تا نسخه‌های بعدی روی نصب‌های قبلی قابل به‌روزرسانی باشند.

## مسیر توسعه

چند مسیر طبیعی برای نسخه‌های بعدی:

- presetهای بیشتر برای مدل‌ها
- مدیریت کامل‌تر تاریخچه‌ی گفتگو
- workspaceهای آماده برای prompt و کدنویسی
- کنترل‌های بیشتر برای شخصی‌سازی تجربه‌ی گفتگو

## لینک‌ها

- [دانلود آخرین نسخه](https://github.com/aishervin/codein/releases)
- [گزارش مشکل یا پیشنهاد](https://github.com/aishervin/codein/issues)
- [Telegram](https://t.me/shervini)
- [X](https://x.com/shervinonx)

<br />

<div dir="rtl" align="center">
  <sub>طراحی و توسعه با تمرکز بر تجربه‌ی گفتگو و کدنویسی · SHΞЯVIN™</sub>
</div>
