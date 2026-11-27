package com.gearmeup;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Gear Me Up",
	description = "Pick a boss and get the best gear setup from what you own (bank, inventory and equipped)",
	tags = {"gear", "setup", "boss", "pvm", "equipment", "bank", "loadout"}
)
@PluginDependency(BankTagsPlugin.class)
public class GearMeUpPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(GearMeUpPlugin.class);
	private static final String BANK_KEY = "bankItems";
	private static final String BANK_SEEN_KEY = "bankSeen";
	private static final Skill[] LEVEL_SKILLS = {Skill.ATTACK, Skill.STRENGTH, Skill.RANGED, Skill.MAGIC, Skill.PRAYER};
	/** Knight Waves training ground progress: this value or more means Chivalry and Piety are unlocked. */
	private static final int KNIGHT_WAVES_DONE = 8;


	/** Bank tag (provided in code, never saved) that matches only the suggested items. */
	private static final String BANK_TAG = "gear me up";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private GearMeUpConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Gson gson;

	@Inject
	private TagManager tagManager;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private SpriteManager spriteManager;

	private GearMeUpPanel panel;
	private BossData bossData;
	private NavigationButton navButton;

	/** Item id -> quantity last seen in the bank (null = not loaded for this profile yet). Client thread only. */
	private Map<Integer, Long> bankItems;
	private SpellAdvisor spellAdvisor;
	private final int[] lastLevels = new int[LEVEL_SKILLS.length];
	private volatile Boss selectedBoss;
	private volatile SetupFinder.Options options;
	private volatile CombatStyle shownStyle;
	private volatile GearReport lastReport;

	/** Item ids in the current suggestion, used by the bank filter. */
	private volatile Set<Integer> suggestedIds = Collections.emptySet();
	private volatile boolean bankFilterOn;

	@Provides
	GearMeUpConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GearMeUpConfig.class);
	}

	@Override
	protected void startUp()
	{
		try
		{
			bossData = BossData.load(gson);
			spellAdvisor = new SpellAdvisor(SpellData.load(gson));
		}
		catch (IOException | RuntimeException e)
		{
			log.error("Gear Me Up: couldn't load its data files", e);
			throw new IllegalStateException("Gear Me Up couldn't load its boss list", e);
		}
		panel = new GearMeUpPanel(this, itemManager, spriteManager, bossData.getBosses());
		navButton = NavigationButton.builder()
			.tooltip("Gear Me Up")
			.icon(createIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		tagManager.registerTag(BANK_TAG, itemId -> suggestedIds.contains(itemId));
	}

	@Override
	protected void shutDown()
	{
		bankFilterOn = false;
		clientThread.invokeLater(this::closeBankFilter);
		tagManager.unregisterTag(BANK_TAG);
		suggestedIds = Collections.emptySet();
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		bankItems = null;
		selectedBoss = null;
		lastReport = null;
	}

	/** Called from the panel (Swing thread): work out the best setups for a boss. */
	void find(Boss boss, SetupFinder.Options opts)
	{
		selectedBoss = boss;
		options = opts;
		clientThread.invokeLater(this::recalculate);
	}

	/** Called from the panel when a style tab is shown, so the bank filter shows that tab's gear. */
	void showingStyle(CombatStyle style)
	{
		shownStyle = style;
		clientThread.invokeLater(this::updateSuggestedIds);
	}

	boolean includeBank()
	{
		return config.includeBank();
	}

	boolean includeInventory()
	{
		return config.includeInventory();
	}

	/** Changes a plugin setting from the panel's advanced options. */
	void setOption(String key, boolean value)
	{
		configManager.setConfiguration(GearMeUpConfig.GROUP, key, value);
	}

	/** Called from the panel: show only the suggested items in the bank (now, and whenever it's opened). */
	void setBankFilter(boolean on)
	{
		bankFilterOn = on;
		clientThread.invokeLater(on ? this::applyBankFilter : this::closeBankFilter);
	}

	/** Client thread. Opens our tag if the bank is open; otherwise it's applied when the bank next opens. */
	private void applyBankFilter()
	{
		if (bankFilterOn && client.getWidget(InterfaceID.Bankmain.ITEMS) != null)
		{
			bankTagsService.openBankTag(BANK_TAG, BankTagsService.OPTION_NO_LAYOUT);
		}
	}

	private void closeBankFilter()
	{
		if (BANK_TAG.equals(bankTagsService.getActiveTag()))
		{
			bankTagsService.closeBankTag();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && bankFilterOn)
		{
			// Wait until the bank has finished building before filtering it.
			clientThread.invokeLater(this::applyBankFilter);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.BANK)
		{
			Map<Integer, Long> items = new TreeMap<>();
			for (Item i : event.getItemContainer().getItems())
			{
				if (i.getId() > 0 && i.getQuantity() > 0)
				{
					items.merge(i.getId(), (long) i.getQuantity(), Long::sum);
				}
			}
			configManager.setRSProfileConfiguration(GearMeUpConfig.GROUP, BANK_SEEN_KEY, String.valueOf(System.currentTimeMillis()));
			if (items.equals(bankItems))
			{
				return; // nothing changed (e.g. just moving items around)
			}
			bankItems = items;
			configManager.setRSProfileConfiguration(GearMeUpConfig.GROUP, BANK_KEY,
				items.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(",")));
		}
		else if (id != InventoryID.INV && id != InventoryID.WORN)
		{
			return;
		}

		if (selectedBoss != null)
		{
			recalculate();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int id = event.getVarbitId();
		if (selectedBoss != null && (id == VarbitID.SPELLBOOK || id == VarbitID.PRAYER_RIGOUR_UNLOCKED
			|| id == VarbitID.PRAYER_AUGURY_UNLOCKED || id == VarbitID.PRAYER_DEADEYE_UNLOCKED
			|| id == VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED || id == VarbitID.PRAYER_ZEAL_UNLOCKED))
		{
			recalculate();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		// Only on a level change, not every XP drop.
		for (int i = 0; i < LEVEL_SKILLS.length; i++)
		{
			if (event.getSkill() == LEVEL_SKILLS[i] && event.getLevel() != lastLevels[i])
			{
				lastLevels[i] = event.getLevel();
				if (selectedBoss != null)
				{
					recalculate();
				}
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// May be a different account: reload the bank snapshot for this profile.
			bankItems = null;
			if (selectedBoss != null)
			{
				recalculate();
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (GearMeUpConfig.GROUP.equals(event.getGroup()) && !BANK_KEY.equals(event.getKey())
			&& !BANK_SEEN_KEY.equals(event.getKey()) && selectedBoss != null)
		{
			clientThread.invokeLater(this::recalculate);
		}
	}

	/** Gathers owned gear and works out every style's best setup. Must run on the client thread. */
	private void recalculate()
	{
		Boss boss = selectedBoss;
		SetupFinder.Options opts = options;
		GearMeUpPanel p = panel;
		if (boss == null || opts == null || p == null)
		{
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			SwingUtilities.invokeLater(() -> p.showMessage("Log in to see your gear for " + boss.getName() + "."));
			return;
		}

		Map<Integer, Candidate> owned = new HashMap<>();
		Map<String, Long> quantities = new HashMap<>();
		Map<String, Integer> itemIds = new HashMap<>();
		addContainer(InventoryID.WORN, ItemSource.EQUIPPED, owned, quantities, itemIds);
		if (config.includeInventory())
		{
			addContainer(InventoryID.INV, ItemSource.INVENTORY, owned, quantities, itemIds);
		}

		String bankStatus;
		if (config.includeBank())
		{
			Map<Integer, Long> bank = loadBank();
			if (bank == null)
			{
				bankStatus = "Bank not seen yet: open your bank once to include it.";
			}
			else
			{
				bank.forEach((itemId, qty) -> addItem(itemId, qty, ItemSource.BANK, owned, quantities, itemIds));
				bankStatus = bankSeen();
			}
		}
		else
		{
			bankStatus = "Bank items are turned off in the advanced options.";
		}

		PlayerContext player = playerContext(quantities, itemIds);
		Boss generic = bossData.getBosses().stream().filter(b -> b.getName().startsWith("Any monster")).findFirst().orElse(null);
		SetupFinder finder = new SetupFinder(boss, generic, owned.values(), player, opts, ignoredNames(),
			bossData.getKnowledge(), spellAdvisor, client.getVarbitValue(VarbitID.SPELLBOOK), quantities);
		Map<CombatStyle, SetupFinder.StyleResult> results = finder.findAll();

		Map<Boost, Integer> boostIcons = new EnumMap<>(Boost.class);
		for (Boost b : Boost.values())
		{
			int id = b.getIconItem() == null ? -1 : player.itemId(b.getIconItem());
			if (id > 0)
			{
				boostIcons.put(b, id);
			}
		}
		GearReport report = new GearReport(boss, results, SetupFinder.best(results), owned.size(), bankStatus, boostIcons);
		lastReport = report;
		updateSuggestedIds();
		SwingUtilities.invokeLater(() -> p.showReport(report));
	}

	/** Client thread. The bank filter shows the gear of the style tab the player is looking at. */
	private void updateSuggestedIds()
	{
		GearReport report = lastReport;
		if (report == null)
		{
			return;
		}
		CombatStyle style = shownStyle != null ? shownStyle : report.best;
		SetupFinder.StyleResult r = style == null ? null : report.results.get(style);
		Set<Integer> ids = new HashSet<>();
		if (r != null)
		{
			r.gear.getPicks().values().forEach(c -> ids.add(c.getItemId()));
		}
		if (!ids.equals(suggestedIds))
		{
			suggestedIds = Collections.unmodifiableSet(ids);
			if (BANK_TAG.equals(bankTagsService.getActiveTag()))
			{
				applyBankFilter(); // refresh the filtered bank view
			}
		}
	}

	private PlayerContext playerContext(Map<String, Long> quantities, Map<String, Integer> itemIds)
	{
		int[] levels = new int[LEVEL_SKILLS.length];
		int[] current = new int[4];
		for (int i = 0; i < LEVEL_SKILLS.length; i++)
		{
			levels[i] = client.getRealSkillLevel(LEVEL_SKILLS[i]);
			lastLevels[i] = levels[i];
			if (i < current.length)
			{
				current[i] = client.getBoostedSkillLevel(LEVEL_SKILLS[i]);
			}
		}
		Set<OffensivePrayer.Unlock> unlocks = EnumSet.noneOf(OffensivePrayer.Unlock.class);
		if (client.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE) >= KNIGHT_WAVES_DONE)
		{
			unlocks.add(OffensivePrayer.Unlock.KNIGHT_WAVES);
		}
		if (client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED) > 0)
		{
			unlocks.add(OffensivePrayer.Unlock.RIGOUR);
		}
		if (client.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED) > 0)
		{
			unlocks.add(OffensivePrayer.Unlock.AUGURY);
		}
		if (client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED) > 0)
		{
			unlocks.add(OffensivePrayer.Unlock.DEADEYE);
		}
		if (client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) > 0)
		{
			unlocks.add(OffensivePrayer.Unlock.MYSTIC_VIGOUR);
		}
		if (client.getVarbitValue(VarbitID.PRAYER_ZEAL_UNLOCKED) > 0)
		{
			unlocks.add(OffensivePrayer.Unlock.ZEAL);
		}
		return new PlayerContext(levels, current, unlocks, quantities, itemIds);
	}

	private String bankSeen()
	{
		String saved = configManager.getRSProfileConfiguration(GearMeUpConfig.GROUP, BANK_SEEN_KEY);
		if (saved == null)
		{
			return "Bank remembered. Open it to refresh.";
		}
		try
		{
			long minutes = (System.currentTimeMillis() - Long.parseLong(saved)) / 60000;
			if (minutes < 1)
			{
				return "Bank seen just now";
			}
			if (minutes < 60)
			{
				return "Bank seen " + minutes + " min ago";
			}
			long hours = minutes / 60;
			if (hours < 48)
			{
				return "Bank seen " + hours + " h ago";
			}
			return "Bank seen " + (hours / 24) + " days ago. Open it to refresh.";
		}
		catch (NumberFormatException e)
		{
			return "Bank remembered. Open it to refresh.";
		}
	}

	private void addContainer(int id, ItemSource source, Map<Integer, Candidate> owned, Map<String, Long> quantities,
		Map<String, Integer> itemIds)
	{
		ItemContainer container = client.getItemContainer(id);
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				addItem(item.getId(), item.getQuantity(), source, owned, quantities, itemIds);
			}
		}
	}

	private void addItem(int itemId, long quantity, ItemSource source, Map<Integer, Candidate> owned, Map<String, Long> quantities,
		Map<String, Integer> itemIds)
	{
		ItemComposition comp = itemManager.getItemComposition(itemId);
		if (comp.getPlaceholderTemplateId() != -1)
		{
			return; // bank placeholder: not actually owned
		}
		if (comp.getNote() != -1)
		{
			itemId = comp.getLinkedNoteId(); // noted item: use the unnoted version
			comp = itemManager.getItemComposition(itemId);
		}
		quantities.merge(comp.getName(), quantity, Long::sum); // used for rune and potion counts
		itemIds.putIfAbsent(comp.getName(), itemId);

		ItemStats stats = itemManager.getItemStats(itemId);
		if (stats == null || !stats.isEquipable() || stats.getEquipment() == null)
		{
			return;
		}

		Candidate existing = owned.get(itemId);
		if (existing == null || source.ordinal() < existing.getSource().ordinal())
		{
			owned.put(itemId, new Candidate(itemId, comp.getName(), source, stats.getEquipment()));
		}
	}

	/** Saved as "id:qty,id:qty". Older saves without quantities ("id,id") count as 1 each. */
	private Map<Integer, Long> loadBank()
	{
		if (bankItems == null)
		{
			String saved = configManager.getRSProfileConfiguration(GearMeUpConfig.GROUP, BANK_KEY);
			if (saved == null || saved.isEmpty())
			{
				return null;
			}
			try
			{
				Map<Integer, Long> items = new TreeMap<>();
				for (String entry : saved.split(","))
				{
					String[] parts = entry.split(":");
					items.put(Integer.parseInt(parts[0]), parts.length > 1 ? Long.parseLong(parts[1]) : 1L);
				}
				bankItems = items;
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}
		return bankItems;
	}

	private List<String> ignoredNames()
	{
		String raw = config.ignoredItems();
		if (raw == null || raw.trim().isEmpty())
		{
			return Collections.emptyList();
		}
		return Arrays.stream(raw.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());
	}

	/** Small helmet icon drawn in code so the plugin needs no image resources. */
	private static BufferedImage createIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0xB0BEC5));
		g.fillArc(2, 2, 12, 14, 0, 180);
		g.fillRect(2, 8, 12, 5);
		g.setColor(new Color(0x37474F));
		g.fillRect(4, 9, 8, 2);
		g.setStroke(new BasicStroke(1.5f));
		g.setColor(new Color(0xFF9800));
		g.drawLine(8, 1, 8, 5);
		g.dispose();
		return img;
	}
}
