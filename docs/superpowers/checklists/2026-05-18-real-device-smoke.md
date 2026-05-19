# Smoke на реальном Android — пошаговая инструкция

**Дата:** 2026-05-18
**Цель:** проверить map-tap события (одиночные маркеры + кластеры) в Yandex MapKit на реальном устройстве. На AVD не работают (см. BUG-1 в [day10-smoke](2026-05-18-day10-smoke.md)). Это блокер для Day 10 checkpoint.

---

## Что у тебя должно быть

- [ ] Реальный Android-телефон (любой Android 8+)
- [ ] USB-кабель к Mac
- [ ] Backend запущен в Docker: `docker ps | grep cleancity` — должны быть оба контейнера (`backend` и `db`)

Если backend не запущен:
```bash
cd ~/Desktop/Myapp/cleancity-kmp && docker compose up -d
sleep 5
curl -s http://localhost:8081/health   # должен вернуть {"status":"ok"}
```

---

## Шаг 1 — Включить отладку по USB на телефоне

Это нужно сделать **на телефоне один раз**:

1. Открой **Настройки → О телефоне** (или «О устройстве»).
2. Найди строку **Номер сборки** (Build number) — обычно в самом низу или в подразделе «Информация о ПО».
3. Тапни по «Номер сборки» **7 раз подряд**. Появится сообщение «Вы стали разработчиком».
4. Вернись в Настройки → найди **«Для разработчиков»** (обычно в разделе «Система» или внизу основного списка).
5. Включи **Отладка по USB** (USB debugging) — переключатель ON.

> Если разделов на русском нет: путь тот же на английском — Settings → About phone → 7 taps on Build number → Settings → Developer options → USB debugging ON.

---

## Шаг 2 — Подключить телефон к Mac

1. Подключи телефон USB-кабелем к Mac.
2. На телефоне появится диалог **«Разрешить отладку USB?»** — поставь галку «Всегда разрешать с этого компьютера» и нажми **OK**.
3. На Mac проверь, что телефон виден:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb devices
   ```
   Должна быть строка типа `XYZ123456789  device` (не `unauthorized`, не `offline`).

> Если `unauthorized` — на телефоне снова появится диалог разрешения, прими его.
> Если `offline` — отключи и подключи кабель заново, или попробуй другой кабель / USB-порт (некоторые кабели только для зарядки).
> Если устройство не появляется вообще — на телефоне переключи USB-режим: при подключении проведи шторку, тапни уведомление «Зарядка через USB», выбери **«Передача файлов» (MTP)**.

---

## Шаг 3 — Прокинуть localhost:8081 с Mac на телефон

Чтобы приложение на телефоне могло достучаться до backend, который крутится на Mac:

```bash
~/Library/Android/sdk/platform-tools/adb reverse tcp:8081 tcp:8081
```

Это говорит: «когда приложение на телефоне ходит на `localhost:8081`, направь это на `localhost:8081` Mac». Работает только пока кабель подключён.

Проверь:
```bash
~/Library/Android/sdk/platform-tools/adb reverse --list
# должен вывести: (reverse) tcp:8081 tcp:8081
```

---

## Шаг 4 — Установить APK на телефон

APK уже пересобран мной с `localhost:8081` (вместо AVD-шного `10.0.2.2:8081`). Файл:
`~/Desktop/Myapp/cleancity-kmp/composeApp/build/outputs/apk/debug/composeApp-debug.apk`

Команда:
```bash
~/Library/Android/sdk/platform-tools/adb install -r ~/Desktop/Myapp/cleancity-kmp/composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Если флаг `-r` не сработал (например установлена другая подпись) — сначала удали:
```bash
~/Library/Android/sdk/platform-tools/adb uninstall com.example.cleancity
~/Library/Android/sdk/platform-tools/adb install ~/Desktop/Myapp/cleancity-kmp/composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

> Если телефон скажет «Запретить установку из неизвестных источников» — на самом телефоне дай разрешение в Настройках для приложения **Установщик пакетов**.

---

## Шаг 5 — Запустить приложение и проверить, что backend виден

1. Открой приложение «Чистый Город» на телефоне (иконка с ⟳ ).
2. Зайди как гость или войди как `agababanz07@gmail.com` (если помнишь пароль). Лучше как гость — быстрее.
3. Открой вкладку **Карта**.

**Если карта серая / пустая / без маркеров:**
- Backend недоступен. Проверь:
  ```bash
  curl -s http://localhost:8081/health
  ~/Library/Android/sdk/platform-tools/adb reverse --list
  ```
- Если `adb reverse` пустой — выполни шаг 3 заново (после reconnect кабеля forward сбрасывается).

**Если карта рендерится и видны маркеры/кластеры с числами:** ок, переходи к смоку.

---

## Шаг 6 — Сами смоук-сценарии

### Сценарий A — Одиночный маркер → MarkerPreview → Detail (**главное, #11**)

1. На карте найди **одиночную синюю pin** (без числа внутри). Если все маркеры в кластерах — зумни пальцами (pinch-out) пока кластер не распадётся.
2. Тапни прямо на pin.
3. **Ожидаемо:** снизу выезжает bottom-sheet с категорией, статусом, координатами и **активной кнопкой «Открыть детально»** (зелёная, не серая, без надписи «Day 10»).
4. Тапни «Открыть детально».
5. **Ожидаемо:** sheet закрывается, открывается экран жалобы с фото-pager'ом, голосовой кнопкой, описанием, историей статусов.
6. Нажми ←  (стрелку назад) → должен вернуться на карту без sheet.

**Если шаг 2 не работает (нет bottom-sheet):** записать как BUG, пробовать с разных pin'ов. Возможно нужно тапать точнее в icon, а не рядом.

**Если bottom-sheet открылся, но кнопка disabled или говорит «Day 10»:** это означает что установился старый APK. Удали + переустанови (см. шаг 4).

### Сценарий B — Cluster tap zoom (**Smoke #7, известный Day 9 баг**)

1. На карте найди **кластер** (круг с числом, например «6» или «3»).
2. Тапни на кластер.
3. **Ожидаемо:** камера зумит на ~1.5 уровня к центру кластера, маркеры расходятся.
4. **Если не зумит:** записать как BUG, переходить к workaround (я помогу).

### Сценарий C — Прочие сценарии Day 10, которые я не смогла проверить на AVD

Прогнать по чек-листу из [day10-smoke.md](2026-05-18-day10-smoke.md), раздел «Не проверено автоматизацией»:
- [ ] **#8 Pull-to-refresh** — свайп пальцем вниз на ленте → должен появиться spinner → список обновится
- [ ] **#9 Пагинация** — скролл вниз до конца ленты → подгружается следующая страница
- [ ] **#13 Vote — авторизованный toggle/untoggle** — залогинься, открой любую жалобу, тап по «Подтверждаю» → счётчик +1, кнопка серая «Вы подтвердили»; повторный тап → счётчик -1
- [ ] **#14 Vote — сетевая ошибка** — останови backend (`docker compose stop backend`), тап vote → snackbar с ошибкой, счётчик откатывается; запусти backend обратно (`docker compose start backend`)
- [ ] **#16 Toggle «Мои» авторизованный** — на ленте тап «Мои» → видны только твои жалобы

---

## Шаг 7 — Откатить URL обратно для AVD

**ВАЖНО:** после теста на реальном устройстве, чтобы AVD снова работал, верни URL:

```bash
sed -i '' 's|http://localhost:8081|http://10.0.2.2:8081|' ~/Desktop/Myapp/cleancity-kmp/local.properties
cd ~/Desktop/Myapp/cleancity-kmp && ./gradlew :composeApp:assembleDebug
```

---

## Шаг 8 — Прислать результаты

Просто напиши в чат:
- A: pass/fail (если fail — что увидела, скриншот можно из шторки телефона → AirDrop / Telegram себе → перетащить мне в чат)
- B: pass/fail
- C: какие из пунктов pass, какие fail

Я по результатам:
1. Если A pass → закрываю Day 10
2. Если A fail → пишем workaround через `MapInputListener` + геометрический hit-test, повторный смоук
3. Если B fail → известный баг Day 9, фиксим там же
4. Если в C что-то fail → отдельный fix по каждому

---

## Шпаргалка команд (всё в одну строку)

Подключение телефона:
```bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB devices
$ADB reverse tcp:8081 tcp:8081
$ADB install -r ~/Desktop/Myapp/cleancity-kmp/composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Откат для AVD:
```bash
sed -i '' 's|localhost:8081|10.0.2.2:8081|' ~/Desktop/Myapp/cleancity-kmp/local.properties && cd ~/Desktop/Myapp/cleancity-kmp && ./gradlew :composeApp:assembleDebug
```

Backend health:
```bash
curl -s http://localhost:8081/health
```
