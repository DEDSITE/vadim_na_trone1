# Этапы — «Вадим на Троне»

| # | Этап | Статус |
|---|------|--------|
| 0 | Каркас, git, GitHub, подключение друга | ✅ репозиторий github.com/DEDSITE/vadim_na_trone (private), друг dxppelgvnger добавлен (ещё не склонировал) |
| 1 | Хаб: бар «Трон» в ночном квартале | ✅ проверен Димой в игре (14.11.2026). Хвост: кадр для главного меню (`/vadim menushot`) ещё не снят — сделать первым делом в этапе 2 |
| 2 | Катсцена: новости по ТВ в баре (25 сек, ролик в Remotion) → затемнение → вход в заброшку | следующий, новый чат |
| 3+ | Заброшка, газ, комнаты Вадима, босс | обсуждаем позже |

## Этап 1 — что сделано (коммиты cd0f8cc … 7c29ad4)
- **Мир**: измерение `vadim_na_trone:hub` (вечная ночь, пустота). `world/Bar.java` строит кодом квартал и бар один раз на мир (SavedData `vadim_hub_built`, версия `Bar.VERSION=1`; поднять версию = пересборка). `/vadim rebuild` — пересобрать.
- **Бар** (x −9..9, z −10..2, пол y=63, потолок 69): стойка z=−6 (Macaw's counters), стулья, бутылки (mcwholidays potions), полки, ТВ-блок `vadim_na_trone:tv` (0,66,−10) с BER (экран тёмный — в катсцене выводить кадры сюда: `TvRenderer.screen()`), гирлянда из Стрипухоеда в выемке потолка над стойкой, подвесные tavern_lantern, диван + стенд 16 записок в жёлтой гирлянде (запад), витрины с лопатами + таблички + картина «Говнозавр Тимофей» (восток), столики у окон, 4 колонки по углам, окна во всю южную стену, дверь заперта (табличка «ЗАКРЫТО»), кнопка «Готов» (6,65,2) + табличка «ГОТОВЫ? x/y».
- **Квартал**: улица вдоль X (дорога z 8..18, тротуары), дома с окнами (свет/шторы/темнота), магазины с неоновыми вывесками (пасхалки), уличные фонари Macaw's, торцевые дома на концах улицы.
- **Машины** (`world/Traffic.java`, `CarEntity` — позиция считается по времени мира, без рывков в сети): седан (6 цветов), такси, грузовик (GeckoLib, свои geo/текстуры + glowmask фар), буханка «Влог конченных» (блочные модели Стрипухоеда, масштаб 0.6, `VanCarRenderer`), опелелёт летит над улицей (GeckoLib, анимация spin). `/vadim car <sedan|taxi|truck|van|opel> [east]`.
- **Свет**: шейдеры из Стрипухоеда (fog.glsl + core-шейдеры, HorrorPost с параметром Cozy в хабе), слоты: 0–4 фары машин (`CarLights`), 5–8 лампы/фонари (`LampLights`). Тёплый блочный свет (LightTextureMixin), гамма 0 в хабе.
- **Звук**: джаз `bar_jazz` из 4 колонок позиционно (`BarJazz`, attenuation_distance 22), в меню тот же трек (`MenuAudio`). Ванильная музыка выключена.
- **Бармен Тимофей** (`BarmanEntity`, модель игрока, HD-скин 512×512: скин бармена + фото лица Тимофея на передней грани головы), следит головой за гостем. ПКМ → `BarmanScreen` по дизайну A «Латунь и орех» (Claude Design: https://claude.ai/artifact/Pa3gHJkbKAzQkGrokFqf3U), шрифты PT Serif/Sans/Mono (assets/vadim_na_trone/font, лицензия ParaType), ответы мышью или 1–5.
- **Старт**: все в хабе нажали «Готов» → отсчёт 3-2-1 (payload `Countdown`) → пока сообщение «дальше катсцена» (`Hub.tick`, место для запуска катсцены).
- **Главное меню** `BarMenuScreen`: фон `textures/gui/menu_bg.png` (пока временный рисунок), машины за окнами рисуются в прямоугольниках `WINDOWS`, логотип текстом, джаз. `/vadim menushot` ставит игрока (0.5,64,−5.2) лицом к окнам, прячет HUD и сохраняет `run/screenshots/vadim_menu_*.png` → из него сделать новый menu_bg (1920×1080) и выставить `WINDOWS` по стёклам.
- **Сторонние моды** (modRuntimeOnly + в зип друзьям): Macaw's Furniture/Lights/Holidays/Windows/Doors, Sound Physics Remastered, Presence Footsteps + Kirin, Camera Overhaul + Cloth Config, Not Enough Animations (+ libs/TRansition, libs/TRender — в dev Loom не распаковывает jar-in-jar).

## Как проверять (до запуска у Димы)
- Компиляция в облаке: classpath из `build/cpdump` (Gradle сам кладёт при синхронизации) → javac.
- Сервер в облаке: KnotServer с `-Dfabric.development=true -Dfabric.gameMappingNamespace=named -Dfabric.runtimeMappingNamespace=named`, classpath = cpdump без clientOnly/lwjgl + свои классы/ресурсы; RCON для команд. Проверено: мир, сборка бара, все машины.
- Шейдеры: компиляция/линковка через Mesa (EGL surfaceless + ctypes) во всех вариантах define.
- Блоки Macaw's в Bar.java сверяются с blockstate-файлами jar из ~/.gradle/caches/modules-2/files-2.1/maven.modrinth.
- Клиент целиком в облаке не запустить (нет нативных LWJGL под Linux) — картинку проверяет Дима.

## Уроки этапа 1
- Порт Стрипухоеда на 1.21.11 НИКОГДА не запускался — его клиентский код (свет/туман/меню/HorrorPost) считать непроверенным; FogRendererMixin валил игру (буфер «без тумана» был короче — фикс в `FlashlightLights.zero`).
- Для каждого стороннего мода проверять fabric.mod.json `depends` и вложенные jar (META-INF/jars) до того, как просить Диму запускать.
