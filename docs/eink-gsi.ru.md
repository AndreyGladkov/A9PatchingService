# E-ink LineageOS 23.2 (Android 16) для Hisense A9

Образ `system` для Hisense A9 (HLTE556N) на основе LineageOS 23.2 GSI, собранный из исходников, с e-ink-доработками.

Скачать: [релиз eink-gsi-20261107](https://github.com/AndreyGladkov/A9PatchingService/releases/tag/eink-gsi-20261107) — образ `a9-eink-lineage-23.2-20261107.img.xz`, модуль `a9_eink_composer-v5.zip`, `SHA256SUMS`. Исходник модуля — [`magisk/a9_eink_composer`](../magisk/a9_eink_composer).

## Что внутри

- LineageOS 23.2 с Google-сервисами (GAPPS), сборка 2026-11-07.
- E-ink-интерфейс из [LineageOS_gsi_HBPC](https://github.com/dianqk/LineageOS_gsi_HBPC): плоские поверхности с чёрными рамками, без анимаций, статичные жесты, экран блокировки и шторка.
- Монохромная тема по умолчанию, в том числе в мастере настройки.
- A9 Service ([A9PatchingService](https://github.com/AndreyGladkov/A9PatchingService), ветка `eink-android16-scroll-menu`): режимы обновления по приложениям, кнопки A9, подсветка, AOD, плавающее меню, Speed при прокрутке.
- Шрифты стоковые LineageOS (Roboto, в системном интерфейсе Google Sans Flex), эмодзи монохромные (Noto Emoji): цветные на e-ink превращаются в серые пятна.

## Требования

- Hisense A9 (HLTE556N) со стоковым вендором Android 11 и разблокированным загрузчиком.
- Отключённая проверка vbmeta: образ не подписан AVB. Если вы уже ставили на A9 любой GSI, это сделано; если нет — один раз из fastboot: `fastboot flash vbmeta --disable-verity --disable-verification vbmeta.img` (`vbmeta.img` из стоковой прошивки).
- `adb` и `fastboot` (Android SDK Platform-Tools) на компьютере.

## Установка

**Все данные на телефоне будут стёрты**, в том числе внутренняя память. Сначала сохраните всё нужное.

1. Проверьте архив и распакуйте его:
   ```sh
   shasum -a 256 -c SHA256SUMS
   xz -dk a9-eink-lineage-23.2-20261107.img.xz
   ```
2. Перезагрузите телефон в fastbootd и прошейте `system`:
   ```sh
   adb reboot fastboot
   fastboot flash system a9-eink-lineage-23.2-20261107.img
   fastboot -w
   fastboot reboot
   ```
   Ошибка `Erasing 'cache' FAILED (Partition doesn't exist)` на A9 — норма.
3. Первая загрузка идёт около 2 минут. Затем пройдите мастер настройки.
4. A9 Service включается сам. Если его нет в Настройки → Специальные возможности, включите его там.

Root ставится отдельно (Magisk в `boot`) и для работы сборки не нужен.

## Без моргания при смене режима

На A9 моргание при смене режима обновления делает вендорный композитор. Сборка его не меняет, поэтому без доработки вход в Speed при прокрутке и возврат из него моргают переходной волной.

Модуль Magisk `a9_eink_composer-v5.zip` берёт композитор с вашего телефона и правит в нём 14 байт: смена режима, которую помечает «Speed при прокрутке», идёт без переходной волны. Ручные переключения режимов и режимы по приложениям остаются как у вендора. Бинарников Hisense в модуле нет. Если композитор на телефоне другой сборки, модуль откажется ставиться и ничего не изменит.

1. Поставьте Magisk.
2. Magisk → Модули → «Установить из хранилища» → `a9_eink_composer-v5.zip`, перезагрузка.
3. В плавающем меню A9 Service включите **Scroll** в строке Refresh mode.

Откат: отключите модуль в Magisk и перезагрузитесь. Если экран после установки не включился, загрузитесь в безопасном режиме Magisk (зажать «громкость вниз» при загрузке): модули отключатся.

## Если не загрузилась

A9 при неудачной загрузке открывает стоковое recovery. Выбирайте только перезагрузку, **никогда не выбирайте сброс (wipe)**. Вернуться можно прошивкой прежнего образа `system` тем же способом из fastbootd.

## Обновления

Образ подписан ключом автора сборки. Следующие версии этой же сборки ставятся одним `fastboot flash system`, без `fastboot -w`, и данные сохраняются. Переход с любой другой прошивки на эту и обратно — только со стиранием данных.

## Благодарности и лицензии

LineageOS, TrebleDroid и [MisterZtr](https://github.com/MisterZtr), [dianqk](https://github.com/dianqk/LineageOS_gsi_HBPC) (HBPC), [damianmqr](https://github.com/damianmqr) (A9PatchingService). Шрифт Noto Emoji — SIL Open Font License 1.1.
