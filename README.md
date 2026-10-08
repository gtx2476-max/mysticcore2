# MysticCore (Purpur / Paper 1.21.8, Java 21)

Один плагин вместо MysticChests + AirDrops + Spheres. УДАЛИ старые jar из plugins/ (MysticChests, AirDrops, Spheres)!
Балансы токенов и монеток подтягиваются автоматически из plugins/MysticChests/tokens.yml и coins.yml.

## Сборка
Залей содержимое папки на GitHub -> вкладка Actions -> Build -> Artifacts -> MysticCore (внутри MysticCore.jar).

## Что внутри
- Мистические сундуки (5 уровней) — автоспавн волнами каждые 3 мин, по 3 штуки (config: drops.*). Общий виртуальный инвентарь: лут НЕ дублируется,
  сундук исчезает, когда пуст. Голограмма с отсчётом, координаты в чате.
- Сферы (9 шт.): Фугу, Посейдон, Аид, Зевс (18% настоящая молния), Гермес, Афина, Арес, Аполлон. Это головы: поставить нельзя.
- Талисманы (6), кейсы (3), расходники (3), уникальные предметы ивента (4).
- Чёрный рынок: торговец в случайной точке, координаты в чат, цены меняются каждые 30 сек, закрывается через 10 мин. Монетки. /bm
- Донат маркет (/donate, токены, высокие цены): премиум аирдропы и сферы.
- Магазин за монетки (/shop), аукцион (/ah, /ah sell <цена>).
- Мама Фугу каждые 7 мин в Мёртвом озере, обмен фугу у Фугафага (5 фугу = 250 токенов).
- Лобби (void-мир): NPC «Выбор режима» (пока только Выживание) -> спавн выживания -> /rtp. В лобби команды отключены (кроме mystic.admin).
- Автошахта в лобби (обновляется каждые 3 мин), награды за онлайн, ежедневный бонус.

## Команды
/menu /donate /shop /ah /coins /rtp /lobby /mine /bm /airdrops /spheres /mystic
Админ (mystic.admin): /mystic spawn <poor|solid|rich|elite|crusher> [here], /mystic wave, /mystic boss, /mystic bossoff,
/mystic tokens <add|set> <игрок> <n>, /mystic give <игрок> <id> [n], /mystic reload, /bm open|close, /coins <add|set> <игрок> <n>,
/spheres give <игрок> <id|all> [n]

## Структура кода (для ИИ)
MysticCore (главный, геттеры) — Economy, Catalog (талисманы/кейсы/расходники/уникальные), DropManager+Drop+LootTables (сундуки),
BlackMarket, Menus+Gui (все меню), AuctionManager, BossManager, LobbyManager, RewardManager, Rtp, Commands,
ItemListener (кейсы/расходники), TalismanManager, пакет spheres (SphereType, SpheresManager, SphereListener, SphereItems, SphereMenu).
Все числа и цены — config.yml.

## Задания, пропуск, скупщик (добавлено)
- /quests: 3 ежедневных + 2 недельных задания (убить мобов, добыть руду, продать ресурсы, открыть сундук, босс и т.д.). Награда — монетки MysticCore + XP пропуска. Сброс в полночь (quests.timezone-offset-hours).
- /season: сезонный пропуск, 50 уровней, бесплатная и премиум (за токены) ветки, сезон 30 дней. Админ: /season xp <игрок> <n>.
- /sell (или NPC «Скупщик» в лобби): продаёт ресурсы из инвентаря за монетки по текущим ценам. /sell hand — предмет в руке.
- /bourse: таблица цен (спрос, тренды ▲▼), клик по предмету продаёт такие. Цены падают от продаж, восстанавливаются со временем, раз в 15 мин — рыночное событие в чате.
- Все базовые цены — config.yml -> exchange.prices. Классы: Exchange, QuestManager, SeasonPass.
