# Hisense A9 на Android 16: рабочие заметки для будущего гайда

Черновик. Записывается по ходу перехода с LineageOS 21 GSI (Android 14) на LineageOS 23.2 GSI (Android 16), 2026-11.

## Исходное состояние телефона

- Hisense A9 (HLTE556N), Snapdragon 662 (bengal), ядро 4.19.157, вендор Android 11 (VNDK 30, SELinux 30.0).
- Загрузчик разблокирован, vbmeta с отключённой проверкой.
- LineageOS 21 GSI, пропатченный A9PatchingService, Magisk 30.6 в boot.

## Какой GSI подходит

Требования вендора A9 к GSI:

| Требование | Почему |
|---|---|
| arm64, A/B, **ext4** (не EROFS) | EROFS нужно ядро 5.4+, у A9 4.19 |
| VNDK v30 в `system_ext/apex` | вендорные HAL собраны под Android 11 |
| `system/etc/selinux/mapping/30.0.cil` | без него init не собирает политику SELinux и уходит в fastboot |
| обвязка TrebleDroid (`phhsu_daemon`, `vndk.rc`) | на ней запускается демон e-ink |
| подпись тестовым ключом AOSP | только тогда патчер может пропатчить SystemUI, TrebleApp и Dialer |

Что проверено:

| GSI | Ключ | Результат |
|---|---|---|
| MisterZtr LineageOS 23.2 VANILLA-EXT4 (2026-05-24) | личный | **работает**, без правок SystemUI |
| Doze-off LineageOS 23.2 / crDroid 12.12 | личный | не запускали, ключ не подходит под полный патч |
| ponces AOSP 16 (2025-06) | личный | не запускали |
| TrebleDroid AOSP 16 (ci-20250617) | тестовый | подходит под полный патч, но старый (июнь 2025) |
| Aizner LineageOS 24.0 (Android 17, 2026-10-29) | тестовый | **не загружается**: нет VNDK 30 и SELinux 30.0; с добавленными VNDK v30 и mapping 30.0 падает в fastboot сразу после логотипа |

## Сборка образа патчером

Патчеру нужен Linux (loop-mount, `setfattr`). На Mac — через Lima VM:

```sh
brew install lima
limactl create --name=a9build --cpus=4 --memory=4 template:ubuntu-lts
limactl start a9build
limactl shell a9build -- sudo apt-get install -y openjdk-17-jre-headless e2fsprogs attr python3 python-is-python3 zipalign apksigner p7zip-full
```

Для повседневного телефона брать **GAPPS**-вариант (`...-GAPPS-EXT4-GSI`): в VANILLA нет Google Play Services, и у восстановленных приложений не приходят push-уведомления (FCM), не работают вход через Google и Play Маркет, часть банков не запускается. С VANILLA на GAPPS той же сборки можно перейти прошивкой одного `system` без `fastboot -w`: ключ подписи у них общий. Проверено на A9: приложения с данными, Magisk, Vector и настройки остались, сервисы Google появились.

Образ MisterZtr лежит на SourceForge. Сервер по умолчанию бывает очень медленным (100–300 КБ/с); быстрее скачивать с конкретного зеркала, например:

```sh
curl -L -o lineage.7z "https://downloads.sourceforge.net/project/misterztr-gsi/LineageOS/Android%2016/LineageOS-23.2-20260524-VANILLA-EXT4-GSI.7z?use_mirror=altushost-swe"
```

Нужен apktool **2.x** (в 3.x нет `apktool b -c`): скачать `apktool_2.12.1.jar` с GitHub iBotPeaches/Apktool и сделать обёртку `/usr/local/bin/apktool`.

```sh
cd system_img_patcher
sudo bash patch_system_img.sh /path/to/LineageOS-23.2-...-VANILLA-EXT4-GSI.img
```

Чтобы в образ сразу попала своя сборка A9 Service (например, с перекраской), положить её в папку патчера как `a9service.apk`.

## Проверка образа без прошивки (DSU)

DSU ставит образ временной системой в `/data`, текущая система и данные не трогаются. Нужен root (Magisk) на текущей системе.

```sh
adb push system_patched.img /data/local/tmp/a16.img
adb shell su -c 'mv /data/local/tmp/a16.img /data/gsi/a16.img && chcon u:object_r:gsi_data_file:s0 /data/gsi/a16.img'
adb shell su -c 'gsi_tool install --no-reboot --gsi-size <размер файла в байтах> --userdata-size 8589934592 < /data/gsi/a16.img'
adb shell su -c 'rm /data/gsi/a16.img; gsi_tool enable --single-boot'
adb reboot
```

- Без `chcon` установка падает: `gsid` не может читать файлы с меткой `shell_data_file`.
- Без `--no-reboot` `gsi_tool` сам перезагружает телефон в постоянный (не однократный) DSU.
- С `--single-boot` любая следующая перезагрузка возвращает на основную систему.
- Если DSU не загрузился до конца, `gsid` сам выключает его на следующей загрузке.
- Если телефон ушёл в fastboot: `fastboot continue` (иногда дважды) возвращает на основную систему.
- Удалить DSU: `adb shell su -c 'gsi_tool wipe'`.
- Вернуться из постоянного (не `--single-boot`) DSU: в шторке уведомление «Dynamic System Updates» → развернуть → Restart. `fastboot gsi disable` из fastbootd на A9 не сработал.
- В DSU `su` из adb отвечал `Permission denied`. На прошитой системе было то же самое, и причина оказалась не в DSU (см. «Root после прошивки»).
- Если `mv`, `chcon` или `< /data/gsi/...` внутри `su -c '...; ...'` падают с `Permission denied`, хотя одиночная `su -c 'ls /data/gsi'` проходит: составная команда у Magisk иногда выполняется в контексте `u:r:shell`, а не `u:r:magisk`. Надёжнее записать команды в файл, `adb push` его в `/data/local/tmp` и запустить одной командой `adb shell su -c 'sh /data/local/tmp/dsu-install.sh'`.
- Если загрузка DSU не удалась, A9 уходит не в fastboot, а в стоковый recovery inkOS с двумя пунктами: `recovery` и пункт иероглифами. Выбирать перезагрузку, сброс данных не выбирать. Журналы неудачной загрузки не сохраняются: загрузка recovery затирает pstore.
- Чтобы adb в DSU работал без подтверждения на экране, ключ `~/.android/adbkey.pub` кладётся в образ как `/system/product/etc/security/adb_keys` (на него указывает ссылка `/adb_keys`) с правами 0644 и меткой `u:object_r:adb_keys_file:s0`. Образ, собранный из исходников, несёт AVB-футер с хеш-деревом: любая правка ломает dm-verity, и DSU не загружается. Сначала `avbtool erase_footer --image system.img`, потом правка (`debugfs -w`), потом `e2fsck -fn`.

## Настройки после первой загрузки Android 16

1. Settings → System → Developer options → Window animation scale, Transition animation scale, Animator duration scale → Animation off.
2. Settings → System → Gestures → Navigation mode → Gesture navigation.
3. Settings → System → TrebleDroid Settings → Misc features → Disable HW overlays.

Без них: экран мигает и замирает на промежуточном кадре, видоискатель камеры чёрный, меню A9 закрывается от касания нижней панели.

## Что работает и что нет на Android 16 (MisterZtr 23.2)

Работает: e-ink и режимы обновления, A9 Service и демон, подсветка (яркость и тёплый свет), вибрация, основная камера, Wi-Fi, Bluetooth, звук, сенсоры, GPS-модуль, отпечаток (HAL), USB/adb, сторонние клавиатуры, мобильная связь (проверено с SIM t2, H+), статичный AOD с луной поверх последнего кадра (шейдер `ColorFade` из патча `services.jar`; при выключении экрана сразу после включения система пропускает анимацию гашения, и луна не рисуется — это поведение Android, не поломка).

Не работает или не проверено:
- фронтальная камера: падает вендорный camera provider;
- правки SystemUI (статичный экран блокировки и AOD), подсветка экрана вызова: образ подписан не тестовым ключом;
- VoLTE: нет `ims-caf-u.apk`;
- связь (SIM) и пробуждение громкостью: не проверены.

## Своя сборка GSI из исходников (в работе)

Цель: e-ink LineageOS 23.2 со своим ключом подписи, чтобы убрать анимации и мерцание в самой системе, а не патчами поверх. Основа: [dianqk/LineageOS_gsi_HBPC](https://github.com/dianqk/LineageOS_gsi_HBPC) (сборка под Bigme HiBreak), она форк [MisterZtr/LineageOS_gsi](https://github.com/MisterZtr/LineageOS_gsi) lineage-23.2.

Где собирать. На Mac с Apple Silicon AOSP нативно не собирается. Работает Linux-ВМ Lima (vz + Rosetta) и amd64-контейнер со сборочным окружением. Нужно 12 ядер, 40 ГБ памяти ВМ (один анализ Soong занимает больше 24 ГБ), 32 ГБ swap и около 550 ГБ на диске, лучше регистрозависимом. Rosetta изредка роняет сборочные утилиты (Go, clang, aidl, toc) с segfault. Помогают `GODEBUG=asyncpreemptoff=1`, `NINJA_ARGS="-k 0"` и повтор `make` до пяти раз: ninja доделывает только оставшееся.

Синхронизация:
- `repo init --depth=1`, затем `repo sync -c --no-tags`. Если неглубокая выборка проекта не удалась, repo молча скачивает все ветки с полной историей, и `.repo` вырастает на сотни гигабайт. Долгую синхронизацию запускать так, чтобы её не убил обрыв сессии (`systemd-run --user`, tmux). Пока git работает, не удалять `tmp_pack_*`.
- LFS-проекты (`external/chromium-webview/prebuilt/*`) после прерванной синхронизации остаются наполовину выкачанными. Лечится `git reset --hard && git lfs pull` в каждом.
- HBPC не тянет `prebuilts/vndk/v28`–`v30`. Вендор A9 (Android 11) без VNDK 30 не загрузится, поэтому их нужно добавить локальным манифестом с ревизиями из MisterZtr/treble_manifest.

Патчи. HBPC-коммит `79a37f8` (сводный набор HiBreak) удалил 75 патчей TrebleDroid/MisterZtr: квоты и project_quota на старых ядрах, `MADV_WIPEONFORK` в bionic для ядер до 4.14, совместимость sepolicy 28.0, vndklite и другие. Bigme они не нужны, а A9 (ядро 4.19, вендор 11) без них после форматирования `/data` уходит в recovery. Родитель этого коммита, `7bf212f`, в точности совпадает с MisterZtr lineage-23.2, так что удалённые патчи берутся оттуда (`git show 79a37f8^:<путь>`) и накладываются через `git am`. Кроме них из MisterZtr нужны ещё:
- реверты удаления `com.android.vndk.v28/v29/v30` (`packages/modules/vndk`): AOSP 16 убрал эти APEX;
- `system/linkerconfig` «Disable vndklite handling»;
- `PRODUCT_EXTRA_VNDK_VERSIONS += 30` в `device/phh/treble/base.mk` и `+= 28 29` в продуктовом makefile.

Проверка готового образа: в `system/system_ext/apex/` должны лежать `com.android.vndk.v28.apex`–`v34.apex`.

Первый запуск через DSU (2026-11-06): система загрузилась до мастера настройки. Пока в образе нет e-ink-части A9 Service, панель показывает только кадры, собранные GPU. Первое время экран чёрный: мастер настройки тёмный, а основное окно уходит в аппаратный оверлей. Из adb:
```sh
adb shell cmd uimode night no
adb shell wm density 360
adb shell settings put global device_provisioned 1
adb shell settings put secure user_setup_complete 1
adb shell settings put global development_settings_enabled 1
adb shell am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS
```
Затем в «Для разработчиков» включить «Отключить аппаратные наложения» (через `service call SurfaceFlinger 1008` без root нельзя, `adb root` в сборке выключен). Плотность 360 задаёт патчер (`ro.sf.lcd_density`), а у чистого GSI она 160, и всё мелкое.

## Белая/чёрная палитра системы (hisense-a9-palette)

Модуль Magisk из [Sufixx/hisense-a9-palette](https://github.com/Sufixx/hisense-a9-palette), собранный из исходников (`magisk-module/build.sh`; нужны Android build-tools, JDK и `framework-res.apk` с телефона). Сети и бинарников нет: оверлей цветов через `cmd overlay fabricate`, «сторож» на `inotifyd`, стиль темы `MONOCHROMATIC`, оверлей обводки плиток.

На Android 16 (MisterZtr 23.2):
- фоны системных экранов чисто белые (`system_neutral1_50` = `#ffffffff`), текст и иконки чёрные;
- плитки быстрых настроек монохромные;
- **шторка из коробки серая** (`#bbbbbb`): в Android 16 SystemUI берёт её фон из `shade_panel_fallback` → `system_accent2_200`, а фон уведомлений из `notification_scrim_fallback` → `system_surface_dim_light` и `notification_scrim_base` → `system_accent1_100`. Палитра сделана под Android 14 и красит их в серый. Исправление — сделать эти три цвета белыми в `eink.xml` модуля (`/data/adb/modules/eink_palette/eink.xml` и копия `/data/system/eink.xml`) и применить оверлей заново:
  ```sh
  for c in system_accent2_200 system_surface_dim_light system_accent1_100; do
    sed -i "s#\(target=\"color/$c\" type=\"0x1c\" value=\"\)0x[0-9a-fA-F]*\"#\10xffffffff\"#" eink.xml
  done
  cmd overlay fabricate --target android --name eink --file /data/system/eink.xml
  ```
- обводок у плиток нет: в Android 16 быстрые настройки рисуются через Compose и не используют `qs_tile_background_shape`;
- цветные кружки у пунктов главного экрана «Настроек» остаются: их цвета зашиты в приложение;
- A9 Service при каждом запуске выставляет свою схему (`SPRITZ` и т. п.) и перетирает `MONOCHROMATIC`; цвета палитры при этом остаются сверху благодаря «сторожу».

## Своя сборка A9 Service с перекраской

- `a9service.apk` с перекраской (colors) кладётся в папку патчера и попадает в `/system/priv-app`; модуль Magisk для подмены не нужен.
- Без root работают: режимы обновления, меню, уровни панели по приложениям (контраст, чёрный, белый), тема и акцент.
- Перекраске внутри приложений нужен Vector (LSPosed) поверх Magisk с Zygisk. Точки, которые она перехватывает (`VectorDrawable.draw`, `BaseRecordingCanvas.draw*`, `Instrumentation.callApplicationOnCreate`, `WindowManagerGlobal`), в Android 16 на месте с теми же сигнатурами.
- Область модуля в Vector переносится командой `vector-cli`:
  ```sh
  C=/data/adb/modules/zygisk_vector/cli
  su -c "$C scope ls com.lmqr.ha9_comp_service"          # на старой системе
  su -c "$C modules enable com.lmqr.ha9_comp_service"   # на новой
  su -c "$C scope set com.lmqr.ha9_comp_service com.android.chrome/0 md.obsidian/0 ..."
  ```
- Схема цветов по умолчанию — «System theme (do not override)»: сервис больше не перетирает тему системы (раньше по умолчанию ставил `SPRITZ`). Выбранная вручную схема работает как прежде.

## Смена режима без моргания и Speed при прокрутке

Почему моргает. На A9 волны рисует не только ядро: e-ink-логика есть в вендорном композиторе `/vendor/bin/hw/vendor.qti.hardware.display.composer-service` (журнал `adb logcat | grep 'SDM.*epd'` работает без root). При каждой смене режима обновления (младший байт `epd_display_mode`) он прогоняет изменившиеся пиксели переходной волной на 37–70 кадров — это видимое моргание. Поэтому моргают открытие приложения и «назад», если у приложения в A9 Service другой режим, чем у лаунчера. Отдельно: в клавиатуре ink-keyboard настройка «очищать экран при скрытии клавиатуры» шлёт полную очистку после каждого скрытия.

Что сделано:
- **Композитор** пропатчен (14 байт): смена режима со значением, в котором стоит бит 0x800, идёт без переходной волны; без бита — как у вендора. Ставится модулем Magisk `a9_eink_display`, раздел `vendor` не трогается. Модулю нужно правило `allow init hal_graphics_composer_default process2 nosuid_transition` в `sepolicy.rule`: файлы модулей монтируются с `/data` (nosuid), и без правила init не может запустить композитор в его домене — экран не включится. Откат: `su -c 'touch /data/adb/modules/a9_eink_display/disable'` и перезагрузка, или безопасный режим Magisk (громкость вниз при загрузке).
- **Демон `a9_eink_server`** (`app/external_scripts/root_service/jni`, сборка `ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=jni/Android.mk APP_ABI=arm64-v8a APP_PLATFORM=android-30`):
  - пассивно читает тачскрин (`/dev/input/event3`) и при сдвиге пальца больше 10 px включает Speed с флагом 0x800 — без моргания;
  - через сокет logd следит за журналом композитора и замечает каждое обновление панели;
  - после отпускания пальца, когда панель 0,15 с ничего не рисует (но не дольше 0,8 с: хвост флинга Chrome длится 1–2,7 с), возвращает режим приложения без флага и просит SurfaceFlinger перерисовать экран (`service call SurfaceFlinger 1004`). Переходная волна прогоняется только по пикселям, оставшимся от Speed, то есть дочищается только область прокрутки;
  - если палец остался на экране, а содержимое замерло на 0,4 с, режим приложения тоже возвращается и область прокрутки дочищается; следующий сдвиг пальца снова включает Speed;
  - логика покрыта тестами: `cc -std=c11 tests/scroll_boost_test.c jni/scroll_boost.c -lm`.
- **Меню A9 Service**: режимы обновления и прозрачность AOD — сплошные сегментированные блоки; нижние углы панели концентричны углам экрана (система A9 радиус не сообщает, взято 24dp); блок Colors свёрнут под значок рядом с OFF в строке Static AOD Opacity.
- **A9 Service**: переключатель «Speed While Scrolling» (он же «Scroll ON/OFF» в строке «Refresh mode» плавающего меню) и настройки «Leave Speed When Still For» (0,15 с) и «Keep Speed After Lifting At Most» (0,8 с). Без пропатченного композитора вход в Speed тоже моргает, поэтому по умолчанию режим выключен.
- **Патчер** кладёт в образ новый демон и A9 Service из своей папки. Исправлен патч Dialer: apktool разбирал его со встроенным старым фреймворком, номера ресурсов не совпадали, и пересборка падала; теперь перед разбором ставится `framework-res.apk` из самого образа.

Замеры: от остановки прокрутки до чистого экрана около 0,6 с (0,15 с ожидания и ~0,45 с на переходную волну).

## Перенос данных и прошивка

Переход с LineageOS 21 (тестовые ключи) на Lineage 23.2 от MisterZtr (его ключ) возможен только с очисткой данных: при прошивке поверх системные приложения с другой подписью уводят телефон в бутлуп. Порядок:

1. Полный бэкап разделов через EDL (см. `backuprestore.md` в вики A9) — точка отката.
2. Приложения с данными: Neo Backup (F-Droid, `com.machiav3lli.backup`) с root.
   - Папка бэкапов во внутренней памяти, например `NeoBackup`.
   - Batch → Backup, все пользовательские приложения, APK + Data.
   - Отдельно системное приложение A9 Service (`com.lmqr.ha9_comp_service`): в нём режимы обновления, цвета, уровни панели.
   - SMS, контакты, журнал звонков — системные пункты Neo Backup.
   - Разрешения можно выдать заранее с компьютера, чтобы мастер первого запуска остановился только на выборе папки:
     ```sh
     P=com.machiav3lli.backup
     adb shell su -c "appops set $P GET_USAGE_STATS allow; appops set $P MANAGE_EXTERNAL_STORAGE allow; pm grant $P android.permission.POST_NOTIFICATIONS; dumpsys deviceidle whitelist +$P"
     # root без всплывающего окна Magisk (policy=2 — разрешить навсегда):
     adb shell su -c 'U=$(stat -c %u /data/data/com.machiav3lli.backup); magisk --sqlite "REPLACE INTO policies (uid,policy,until,logging,notification) VALUES($U,2,0,1,1)"'
     ```
   - На A9 бэкап 83 приложений (APK + данные) занял около 4 минут и 9,9 ГБ.
   - Банковские приложения и аутентификаторы после восстановления почти наверняка попросят войти заново: их ключи живут в аппаратном хранилище, которое сбрасывается при очистке данных. Коды Google Authenticator перед прошивкой экспортировать отдельно.
3. Вся внутренняя память вместе с папкой `NeoBackup` — на компьютер. `fastboot -w` стирает и её. Root-архив сохраняет и `Android/data`:
   ```sh
   adb exec-out su -c 'tar -C /data/media/0 -cf - .' > sdcard.tar
   ```
4. Точка отката без EDL: снять текущие разделы на компьютер (нужен root):
   ```sh
   for p in system_a system_ext_a product_a; do adb exec-out su -c "cat /dev/block/mapper/$p" > $p.img; done
   for p in boot_a vbmeta_a vbmeta_system_a; do adb exec-out su -c "cat /dev/block/by-name/$p" > $p.img; done
   ```
   Вернуть старую систему: `fastboot flash system system_a.img` и т. д. из fastbootd, затем `fastboot -w`.
5. Прошивка. `a16_colors.img` — это `system_patched.img`, который выдал патчер из GSI MisterZtr с colors-сборкой `a9service.apk` (см. «Сборка образа патчером»), просто переименованный:
   ```sh
   adb reboot fastboot                   # fastbootd
   fastboot flash system a16_colors.img
   fastboot -w                           # ошибка про cache на A9 — норма, данные очищены
   fastboot reboot
   ```
   Первая загрузка — около 2 минут. Magisk живёт в boot, его прошивка system не трогает.

## Root после прошивки

1. Поставить приложение Magisk той же версии (`adb install Magisk.apk`), открыть, согласиться на «Requires additional setup» — телефон перезагрузится.
2. Если `adb shell su` отвечает `Permission denied`: Magisk → Superuser → «[SharedUID] Shell» — включить. Первый запрос root из adb, оставшийся без ответа на экране, Magisk запоминает как отказ.
3. Zygisk и Vector:
   ```sh
   adb shell su -c "magisk --sqlite \"REPLACE INTO settings (key,value) VALUES('zygisk',1)\""
   adb push Vector-v2.2-3080-Release.zip /data/local/tmp/
   adb shell su -c 'magisk --install-module /data/local/tmp/Vector-v2.2-3080-Release.zip'
   adb reboot
   ```
4. Язык системы без мастера настройки: `adb shell su -c 'setprop persist.sys.locale ru-RU'` и перезагрузка.

## Возврат данных

1. Внутренняя память из архива (без `Android/`, данные приложений вернёт Neo Backup):
   ```sh
   adb shell su -c 'toybox tar -C /data/media/0 -xf - --exclude=./Android --exclude=./Android/*' < sdcard.tar
   ```
   Затем владелец `1023:1023`, права 2775/664 и SELinux-метка как у `DCIM` (`media_rw_data_file` с категориями MediaProvider).
2. Neo Backup на новой системе: поставить заново, выдать разрешения и root теми же командами, в мастере указать ту же папку `NeoBackup`.
3. Restore → Blocklist: отметить **Magisk** и **Neo Backup** (их не восстанавливать: Magisk затрёт текущие настройки root, Neo Backup не должен восстанавливать сам себя). Затем выбрать все APK и данные → Restore. На A9 81 приложение восстановилось примерно за 11 минут, данные вернулись с правильными владельцами.
4. Настройки A9 Service (если сохраняли `shared_prefs` отдельно): остановить приложение, скопировать `shared_prefs` в `/data/data/com.lmqr.ha9_comp_service/`, `chown` на uid приложения, `restorecon -R`, затем перевключить службу специальных возможностей:
   ```sh
   S=com.lmqr.ha9_comp_service/.A9AccessibilityService
   adb shell "settings put secure enabled_accessibility_services ''; settings put secure enabled_accessibility_services $S"
   ```
5. Vector: `modules enable` и `scope set` (см. «Своя сборка A9 Service с перекраской»). После этого модуль загружается в приложения из области (`vector-cli log cat` → `Loading class ...RecolorModule`).
6. SMS и журнал звонков (по желанию): в Neo Backup включить Settings → Advanced → Enable special backups, перезапустить приложение. На время восстановления SMS сделать Neo Backup SMS-приложением по умолчанию (`cmd role add-role-holder --user 0 android.app.role.SMS com.machiav3lli.backup`), потом вернуть `com.android.messaging`. Восстанавливать только `special.smsmms.json` и `special.calllogs.json`: остальные специальные пункты (accounts, wifi, bluetooth, fingerprint) на другой версии Android могут дать бутлуп.
7. В Neo Backup на свежей установке выключено «Restore with all permissions»: восстановленные приложения сами попросят разрешения при первом запуске.
