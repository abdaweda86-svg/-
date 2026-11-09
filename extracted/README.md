# المارد (Genie) - أندرويد

بناء الـ APK عبر GitHub Actions:
1. ارفع كل محتويات هذا المجلد إلى مستودع GitHub جديد (فرع main).
2. Settings > Secrets and variables > Actions > New repository secret
   الاسم: ANTHROPIC_API_KEY  والقيمة: مفتاحك من console.anthropic.com
3. تبويب Actions > Build APK > Run workflow (أو يشتغل تلقائياً مع كل push).
4. بعد انتهاء البناء: افتح التشغيلة > Artifacts > genie-apk > حمّل app-debug.apk وثبّته.

الاستخدام: اضغط على المارد وتكلم. اسحبه لتحريكه. ضغطة طويلة تبدّل الشخصية (مارد/ذكر/بنت).
