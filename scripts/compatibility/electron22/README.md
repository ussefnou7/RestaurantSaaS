# تجربة توافق التخزين مع Electron 22

أداة تشخيص معزولة؛ لا تغيّر حزمة POS ولا بيانات المستخدم ولا تضيف اتصالًا بالخادم.

تأخذ مصدر SQLite وschema وoutbox الفعلي من POS ومكتبة WASM المثبتة، وتحوّل TypeScript فقط إلى JavaScript متوافق مع ES2022. تستخدم غلاف CommonJS وبروتوكولًا محليًا قديمًا لأن غلاف الإنتاج يعتمد واجهات Electron أحدث. نجاحها ليس إثباتًا لتوافق غلاف الإنتاج أو Windows أو الطابعة.

## التشغيل

من جذر مستودع Backend، مع Node وأدوات POS المثبتة:

```sh
node scripts/compatibility/electron22/prepare.mjs ../restaurant-pos /tmp/pos-legacy-probe-new
/path/to/electron-22 /tmp/pos-legacy-probe-new
/path/to/electron-22 /tmp/pos-legacy-probe-new --verify
```

على Windows استخدم مسار electron.exe ومجلدًا مؤقتًا جديدًا. يجب استخدام Electron 22.3.27 من توزيعة Electron الرسمية، والتحقق من SHA-256 مقابل SHASUMS256 المنشور معها. البرنامج لا ينزل runtime تلقائيًا.

- المرحلة الأولى تنشئ قاعدة فعلية عبر OPFS داخل Worker، وتطبق migrations وتكتب طلبًا تجريبيًا.
- المرحلة الثانية في عملية منفصلة تتحقق من بقاء المفتاح والنص العربي والمبلغ والعدد، ثم تؤكد المزامنة وتحذف الصف المتزامن.
- كل مرحلة تكتب write-result.json أو verify-result.json وتنتهي بكود 0 عند النجاح، أو 1 عند الفشل/انتهاء المهلة.
- التخزين محصور داخل isolated-profile في مجلد التجربة. يجب إنشاء مجلد جديد لكل تجربة مستقلة؛ لا تستخدم مجلد بيانات تطبيق حقيقي.
- source-sha256.json يسجل بصمات المصدر. لا تعدل المصادر المأخوذة أثناء المقارنة.

الاختبار يثبت استعادة بعد إنهاء طبيعي للعملية فقط؛ لا يحاكي انقطاع الكهرباء ولا يشغّل API أو Windows spooler. اختبارات 32 و64 بت تتطلب runtime مطابقًا داخل النظام المستهدف، ولا تُستنتج من تشغيل Linux x64.


## تجربة الواجهة والغلاف الحقيقي

`ui-probe.cjs` يحمل الواجهة المبنية وإيصالًا عربيًا صناعيًا، ويحفظ صورًا وJSON، ويحجب HTTP/HTTPS. `stage-shell.mjs` ينسخ المشروع إلى مجلد جديد، ويجرب CommonJS و`legacy-protocol.ts` وهدف chrome108. لا يغير المشروع الأصلي.

```sh
node scripts/compatibility/electron22/stage-shell.mjs ../restaurant-pos /tmp/pos-legacy-shell-new
cd /tmp/pos-legacy-shell-new
node node_modules/typescript/bin/tsc -p tsconfig.electron.json
node node_modules/vite/bin/vite.js build --configLoader runner
cp electron/settings.html dist-electron/electron/settings.html
```

بعد البناء، من مجلد المشروع الذي يحتوي أدوات التجربة:

```sh
/path/to/electron-22 scripts/compatibility/electron22/ui-probe.cjs /tmp/pos-legacy-shell-new/dist /tmp/pos-legacy-ui-results
/path/to/electron-22 scripts/compatibility/electron22/shell-probe.cjs /tmp/pos-legacy-shell-new /tmp/pos-legacy-shell-results
```

`stage-shell` يحتفظ بـpackage.json الأصلي عمدًا؛ شغّل runtime 22 مباشرة كما أعلاه. لا تستخدم npm run build أو dist:win داخل النسخة المؤقتة: إعداد الإصدار والتوزيع النهائي لم يُعدّل بعد. المكتبة المحلية المشتركة node_modules تُقرأ من مشروع POS؛ لم يُجر npm ci، لذلك سجل الاعتماديات الفعلية مع النتيجة.

غلاف الاختبار الأخير يمرر طلبًا اصطناعيًا إلى HTTP loopback، ثم يغلق الخادم لاختبار فشل الشبكة، ويولد بايتات إيصال عبر كود الطباعة الفعلي دون الاتصال بطابعة. يبقى المرور عبر Windows spooler اختبارًا منفصلًا.
