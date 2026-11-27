package com.gearmeup;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.ImageUtil;

/**
 * Side panel: search for a boss, pick prayer/boost options, then see the best setup and DPS for each
 * attack style, laid out like the in-game equipment screen.
 */
class GearMeUpPanel extends PluginPanel
{
	private static final Color WARNING = new Color(0xFF6B6B);
	private static final Color DPS_GREEN = new Color(0x4CD964);
	private static final Color VALUE = ColorScheme.BRAND_ORANGE;
	private static final int SLOT_SIZE = 40;
	private static final int MAX_SUGGESTIONS = 10;

	private static final String AUTO = "Auto (best)";
	private static final String NONE = "None";
	private static final String CURRENT = "Current levels";

	/** Equipment screen layout: {row, column} for each slot. */
	private static final Object[][] GRID = {
		{EquipmentInventorySlot.HEAD, 0, 1, SpriteID.Wornicons.HEAD},
		{EquipmentInventorySlot.CAPE, 1, 0, SpriteID.Wornicons.CAPE},
		{EquipmentInventorySlot.AMULET, 1, 1, SpriteID.Wornicons.NECK},
		{EquipmentInventorySlot.AMMO, 1, 2, SpriteID.Wornicons.AMMUNITION},
		{EquipmentInventorySlot.WEAPON, 2, 0, SpriteID.Wornicons.WEAPON},
		{EquipmentInventorySlot.BODY, 2, 1, SpriteID.Wornicons.TORSO},
		{EquipmentInventorySlot.SHIELD, 2, 2, SpriteID.Wornicons.SHIELD},
		{EquipmentInventorySlot.LEGS, 3, 1, SpriteID.Wornicons.LEGS},
		{EquipmentInventorySlot.GLOVES, 4, 0, SpriteID.Wornicons.HANDS},
		{EquipmentInventorySlot.BOOTS, 4, 1, SpriteID.Wornicons.FEET},
		{EquipmentInventorySlot.RING, 4, 2, SpriteID.Wornicons.RING},
	};

	private final GearMeUpPlugin plugin;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final List<Boss> bosses;
	private final Map<String, BufferedImage> bossImages = new HashMap<>();

	private final IconTextField searchField = new IconTextField();
	private final JPopupMenu suggestionPopup = new JPopupMenu();
	private final DefaultListModel<Boss> suggestionModel = new DefaultListModel<>();
	private final JList<Boss> suggestionList = new JList<>(suggestionModel);
	private boolean updatingSearch;

	private final JLabel bossImage = new JLabel();
	private final JLabel bossName = new JLabel();
	private final JTextArea bossInfo = wrappingText("", ColorScheme.LIGHT_GRAY_COLOR);
	private final JCheckBox slayerBox = new JCheckBox("On slayer task");
	private final JComboBox<Object> prayerBox = new JComboBox<>();
	private final JComboBox<Object> boostBox = new JComboBox<>();
	private final JPanel advanced = new JPanel(new GridLayout(0, 1, 0, 2));
	private final JCheckBox optimiseBox = new JCheckBox("Pick weapon by DPS", true);
	private final JCheckBox bankBox = new JCheckBox("Include bank");
	private final JCheckBox inventoryBox = new JCheckBox("Include inventory");
	private final JButton findButton = new JButton("Find best setup");
	private final JPanel results = new JPanel();

	private static final String BANK_FILTER_OFF = "Show only this gear in bank";
	private static final String BANK_FILTER_ON = "Bank filtered (click to stop)";
	private final JToggleButton bankFilter = new JToggleButton(BANK_FILTER_OFF);

	private Boss selected;
	private GearReport report;
	private CombatStyle shownStyle;
	private boolean userPickedTab;
	private final Map<String, String> setupLabels = new HashMap<>();

	GearMeUpPanel(GearMeUpPlugin plugin, ItemManager itemManager, SpriteManager spriteManager, List<Boss> bosses)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.bosses = bosses.stream().sorted(Comparator.comparing(b -> b.getName().toLowerCase(Locale.ROOT)))
			.collect(Collectors.toList());

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

		JLabel loaded = smallLabel(bosses.size() + " bosses loaded", ColorScheme.MEDIUM_GRAY_COLOR);
		top.add(loaded);
		top.add(spacer(4));

		setUpSearch();
		searchField.setAlignmentX(LEFT_ALIGNMENT);
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		searchField.setPreferredSize(new Dimension(0, 30));
		top.add(searchField);
		top.add(spacer(8));

		top.add(header());
		top.add(spacer(6));

		slayerBox.setToolTipText("Count your slayer helmet / black mask bonus (only works on a slayer task)");
		slayerBox.setAlignmentX(LEFT_ALIGNMENT);
		slayerBox.addActionListener(e -> refind());
		top.add(slayerBox);
		top.add(spacer(4));

		prayerBox.addItem(AUTO);
		prayerBox.addItem(NONE);
		for (OffensivePrayer p : OffensivePrayer.values())
		{
			prayerBox.addItem(p);
		}
		prayerBox.setToolTipText("Offensive prayer for the DPS numbers. Auto uses the best one you've unlocked for each style.");
		prayerBox.addActionListener(e -> refind());
		top.add(labelled("Prayer", prayerBox));
		top.add(spacer(4));

		boostBox.addItem(AUTO);
		boostBox.addItem(NONE);
		boostBox.addItem(CURRENT);
		for (Boost b : Boost.values())
		{
			boostBox.addItem(b);
		}
		boostBox.setToolTipText("Potion boost for the DPS numbers. Auto uses the best potion you own for each style "
			+ "(raid potions only inside their raid). \"Current levels\" uses your levels right now.");
		boostBox.addActionListener(e -> refind());
		top.add(labelled("Boost", boostBox));
		top.add(spacer(6));

		JButton advancedButton = new JButton("Advanced options");
		advancedButton.setAlignmentX(LEFT_ALIGNMENT);
		advancedButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		advancedButton.addActionListener(e ->
		{
			advanced.setVisible(!advanced.isVisible());
			revalidate();
		});
		top.add(advancedButton);

		optimiseBox.setToolTipText("Try every weapon you own and keep the one with the highest DPS. "
			+ "Untick to use the wiki's recommended order instead.");
		optimiseBox.addActionListener(e -> refind());
		bankBox.setSelected(plugin.includeBank());
		bankBox.addActionListener(e -> plugin.setOption("includeBank", bankBox.isSelected()));
		inventoryBox.setSelected(plugin.includeInventory());
		inventoryBox.addActionListener(e -> plugin.setOption("includeInventory", inventoryBox.isSelected()));
		advanced.add(optimiseBox);
		advanced.add(bankBox);
		advanced.add(inventoryBox);
		advanced.setAlignmentX(LEFT_ALIGNMENT);
		advanced.setBorder(new EmptyBorder(4, 4, 4, 4));
		advanced.setVisible(false);
		top.add(advanced);
		top.add(spacer(4));

		findButton.setAlignmentX(LEFT_ALIGNMENT);
		findButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		findButton.setEnabled(false);
		findButton.addActionListener(e -> refind());
		top.add(findButton);

		bankFilter.setToolTipText("Filter the bank so it only shows the gear on the tab you're viewing. Applies whenever the bank is open.");
		bankFilter.setEnabled(false);
		bankFilter.addActionListener(e ->
		{
			boolean on = bankFilter.isSelected();
			bankFilter.setText(on ? BANK_FILTER_ON : BANK_FILTER_OFF);
			plugin.setBankFilter(on);
		});

		results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
		results.setBorder(new EmptyBorder(10, 0, 0, 0));
		showMessage("Search for a boss above. Open your bank once so your banked gear is included.");

		add(top, BorderLayout.NORTH);
		add(results, BorderLayout.CENTER);
	}

	// ---------------------------------------------------------------- search with suggestions

	private void setUpSearch()
	{
		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchField.setToolTipText("Type a boss name or nickname (e.g. kq, cerb, tob)");

		suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		suggestionList.setFocusable(false);
		suggestionList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		suggestionList.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean focus)
			{
				Boss b = (Boss) value;
				JLabel l = (JLabel) super.getListCellRendererComponent(list, b.getName(), index, isSelected, focus);
				l.setFont(FontManager.getRunescapeSmallFont());
				l.setBorder(new EmptyBorder(3, 6, 3, 6));
				l.setToolTipText(b.getCategory());
				l.setIcon(scaled(b, 18));
				return l;
			}
		});
		suggestionList.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				int i = suggestionList.locationToIndex(e.getPoint());
				if (i >= 0)
				{
					choose(suggestionModel.get(i));
				}
			}
		});
		JScrollPane scroll = new JScrollPane(suggestionList);
		scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		suggestionPopup.setLayout(new BorderLayout());
		suggestionPopup.add(scroll, BorderLayout.CENTER);
		suggestionPopup.setFocusable(false);
		suggestionPopup.setBorder(null);

		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateSuggestions();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateSuggestions();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateSuggestions();
			}
		});
		searchField.addClearListener(this::updateSuggestions);
		searchField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				int n = suggestionModel.getSize();
				switch (e.getKeyCode())
				{
					case KeyEvent.VK_DOWN:
						if (!suggestionPopup.isVisible())
						{
							updateSuggestions();
						}
						else if (n > 0)
						{
							suggestionList.setSelectedIndex(Math.min(n - 1, suggestionList.getSelectedIndex() + 1));
							suggestionList.ensureIndexIsVisible(suggestionList.getSelectedIndex());
						}
						e.consume();
						break;
					case KeyEvent.VK_UP:
						if (n > 0)
						{
							suggestionList.setSelectedIndex(Math.max(0, suggestionList.getSelectedIndex() - 1));
							suggestionList.ensureIndexIsVisible(suggestionList.getSelectedIndex());
						}
						e.consume();
						break;
					case KeyEvent.VK_ENTER:
						if (n > 0)
						{
							int i = Math.max(0, suggestionList.getSelectedIndex());
							choose(suggestionModel.get(i));
						}
						e.consume();
						break;
					case KeyEvent.VK_ESCAPE:
						suggestionPopup.setVisible(false);
						break;
					default:
				}
			}
		});
	}

	/** Shows matching bosses under the search box: best matches first, or every boss when the box is empty. */
	private void updateSuggestions()
	{
		if (updatingSearch)
		{
			return;
		}
		String query = searchField.getText() == null ? "" : searchField.getText().trim();
		List<Boss> matches = bosses.stream()
			.filter(b -> b.matches(query))
			.sorted(Comparator.comparingInt((Boss b) -> query.isEmpty() ? 0 : b.matchRank(query))
				.thenComparing(b -> b.getName().toLowerCase(Locale.ROOT)))
			.collect(Collectors.toList());
		suggestionModel.clear();
		matches.forEach(suggestionModel::addElement);
		if (matches.isEmpty())
		{
			suggestionPopup.setVisible(false);
			if (!query.isEmpty())
			{
				showMessage("No boss matches \"" + query + "\". Try another name, or \"Any monster (generic)\".");
			}
			return;
		}
		suggestionList.setSelectedIndex(0);
		suggestionList.setVisibleRowCount(Math.min(MAX_SUGGESTIONS, matches.size()));
		if (searchField.isShowing())
		{
			suggestionPopup.setPopupSize(searchField.getWidth(), suggestionList.getPreferredScrollableViewportSize().height + 4);
			suggestionPopup.show(searchField, 0, searchField.getHeight());
			searchField.requestFocusInWindow();
		}
	}

	private void choose(Boss boss)
	{
		suggestionPopup.setVisible(false);
		updatingSearch = true;
		searchField.setText(boss.getName());
		updatingSearch = false;
		selectBoss(boss);
	}

	// ---------------------------------------------------------------- boss header

	private JPanel header()
	{
		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setAlignmentX(LEFT_ALIGNMENT);
		bossImage.setPreferredSize(new Dimension(84, 84));
		bossImage.setHorizontalAlignment(SwingConstants.CENTER);
		bossImage.setVerticalAlignment(SwingConstants.CENTER);
		header.add(bossImage, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		bossName.setFont(FontManager.getRunescapeBoldFont());
		bossName.setForeground(VALUE);
		bossName.setAlignmentX(LEFT_ALIGNMENT);
		text.add(bossName);
		text.add(spacer(2));
		text.add(bossInfo);
		header.add(text, BorderLayout.CENTER);
		return header;
	}

	private void selectBoss(Boss boss)
	{
		if (boss != selected)
		{
			userPickedTab = false;
			setupLabels.clear();
		}
		selected = boss;
		bossName.setText(boss.getName());
		BufferedImage img = image(boss);
		bossImage.setIcon(img == null ? null : new ImageIcon(img));
		StringBuilder info = new StringBuilder(boss.describe());
		Boss.Combat c = boss.getCombat();
		if (c != null)
		{
			info.append(info.length() > 0 ? " " : "").append(c.getHitpoints()).append(" HP, ").append(c.getDefenceLevel())
				.append(" Defence").append(c.getTarget() == null ? "" : " (" + c.getTarget() + ")").append(".");
		}
		bossInfo.setText(info.toString());
		findButton.setEnabled(true);
		revalidate();
		refind();
	}

	private BufferedImage image(Boss boss)
	{
		return bossImages.computeIfAbsent(boss.getImageSlug(), slug ->
		{
			String path = "npc/" + slug + ".png";
			if (GearMeUpPanel.class.getResource(path) == null)
			{
				return null;
			}
			return ImageUtil.loadImageResource(GearMeUpPanel.class, path);
		});
	}

	private ImageIcon scaled(Boss boss, int size)
	{
		BufferedImage img = image(boss);
		if (img == null)
		{
			return null;
		}
		double f = (double) size / Math.max(img.getWidth(), img.getHeight());
		return new ImageIcon(ImageUtil.resizeImage(img, Math.max(1, (int) (img.getWidth() * f)), Math.max(1, (int) (img.getHeight() * f))));
	}

	// ---------------------------------------------------------------- options

	private void refind()
	{
		if (selected == null)
		{
			return;
		}
		SetupFinder.Options o = new SetupFinder.Options();
		Object prayer = prayerBox.getSelectedItem();
		o.noPrayer = NONE.equals(prayer);
		o.prayer = prayer instanceof OffensivePrayer ? (OffensivePrayer) prayer : null;
		Object boost = boostBox.getSelectedItem();
		o.noBoost = NONE.equals(boost);
		o.currentLevels = CURRENT.equals(boost);
		o.boost = boost instanceof Boost ? (Boost) boost : null;
		o.optimiseWeapon = optimiseBox.isSelected();
		o.onSlayerTask = slayerBox.isSelected();
		o.setupLabels = new HashMap<>(setupLabels);
		if (report == null || report.boss != selected)
		{
			showMessage("Working it out...");
		}
		plugin.find(selected, o);
	}

	// ---------------------------------------------------------------- results

	void showMessage(String message)
	{
		results.removeAll();
		results.add(wrappingText(message, ColorScheme.LIGHT_GRAY_COLOR));
		results.revalidate();
		results.repaint();
	}

	void showReport(GearReport r)
	{
		if (r.boss != selected)
		{
			return; // an older answer for a boss that's no longer selected
		}
		report = r;
		if (!userPickedTab || shownStyle == null || r.results.get(shownStyle).immune)
		{
			shownStyle = r.best != null ? r.best : firstUsable(r);
		}
		bankFilter.setEnabled(true);
		render();
	}

	private static CombatStyle firstUsable(GearReport r)
	{
		for (SetupFinder.StyleResult s : r.results.values())
		{
			if (!s.immune)
			{
				return s.style;
			}
		}
		return CombatStyle.SLASH;
	}

	private void render()
	{
		GearReport r = report;
		SetupFinder.StyleResult shown = r.results.get(shownStyle);
		plugin.showingStyle(shownStyle);
		results.removeAll();

		// Headline: best DPS over all styles.
		if (r.best != null)
		{
			SetupFinder.StyleResult best = r.results.get(r.best);
			JPanel headline = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			headline.setAlignmentX(LEFT_ALIGNMENT);
			JLabel dps = new JLabel(String.format("%.2f DPS", best.dps.getDps()));
			dps.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 20f));
			dps.setForeground(DPS_GREEN);
			JLabel style = new JLabel("  " + groupName(r.best.group()));
			style.setFont(FontManager.getRunescapeBoldFont());
			style.setForeground(DPS_GREEN);
			headline.add(dps);
			headline.add(style);
			headline.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
			results.add(headline);
			results.add(spacer(6));
		}
		else if (r.boss.getCombat() == null)
		{
			results.add(wrappingText("No combat stats for this entry, so no DPS: gear is picked from the wiki lists and stats.",
				ColorScheme.LIGHT_GRAY_COLOR));
			results.add(spacer(6));
		}

		results.add(tabs(r, new String[]{"melee", "ranged", "magic"}, shownStyle.group(), true));
		if (shownStyle.isMelee())
		{
			results.add(tabs(r, new String[]{"Slash", "Crush", "Stab"}, shownStyle.name(), false));
		}
		results.add(spacer(6));

		if (shown.immune)
		{
			results.add(wrappingText(r.boss.getName() + " is immune to " + shownStyle.group() + ".", WARNING));
			results.revalidate();
			results.repaint();
			return;
		}

		if (shown.choices.size() > 1)
		{
			JComboBox<String> setups = new JComboBox<>();
			shown.choices.forEach(s -> setups.addItem(s.getLabel()));
			setups.setSelectedItem(shown.setup.getLabel());
			setups.addActionListener(e ->
			{
				setupLabels.put(shownStyle.group(), (String) setups.getSelectedItem());
				userPickedTab = true;
				refind();
			});
			results.add(labelled("Setup", setups));
			results.add(spacer(6));
		}
		if (!shown.recommended)
		{
			results.add(wrappingText("The wiki doesn't list a " + shownStyle.group() + " setup here, so this uses general gear.",
				ColorScheme.MEDIUM_GRAY_COLOR));
			results.add(spacer(4));
		}

		if (shown.dps != null)
		{
			results.add(statsTable(shown.dps));
			results.add(spacer(8));
		}

		results.add(equipmentGrid(shown.gear));
		results.add(spacer(4));
		Candidate weapon = shown.gear.get(EquipmentInventorySlot.WEAPON);
		if (weapon != null)
		{
			JLabel name = smallLabel(weapon.getName() + " (" + weapon.getSource().getDisplayName() + ")", Color.WHITE);
			name.setHorizontalAlignment(SwingConstants.CENTER);
			results.add(name);
		}
		results.add(spacer(6));

		for (OffensivePrayer p : shown.prayers)
		{
			JLabel l = smallLabel(p.getDisplayName(), Color.WHITE);
			l.setIconTextGap(6);
			spriteManager.addSpriteTo(l, p.getSprite(), 0);
			results.add(l);
			results.add(spacer(2));
		}
		if (shown.prayers.isEmpty() && !NONE.equals(prayerBox.getSelectedItem()))
		{
			results.add(smallLabel("No offensive prayer unlocked for this style", ColorScheme.MEDIUM_GRAY_COLOR));
		}
		if (shown.boost != null)
		{
			JLabel l = smallLabel(shown.boost.getDisplayName()
				+ (shown.boost.getRaid() != null ? " (made in the raid)" : ""), Color.WHITE);
			l.setIconTextGap(6);
			Integer id = r.boostIcons.get(shown.boost);
			if (id != null)
			{
				itemManager.getImage(id).addTo(l);
			}
			results.add(l);
		}
		else if (AUTO.equals(boostBox.getSelectedItem()))
		{
			results.add(smallLabel("No boosting potion owned for this style", ColorScheme.MEDIUM_GRAY_COLOR));
		}

		if (shown.spell != null)
		{
			results.add(spacer(8));
			results.add(spellSection(shown.spell));
		}

		for (String warning : shown.gear.getWarnings())
		{
			results.add(spacer(4));
			results.add(wrappingText("! " + warning, WARNING));
		}

		if (!shown.setup.getNotes().isEmpty())
		{
			results.add(spacer(8));
			results.add(smallLabel("Tips", VALUE));
			results.add(wrappingText(shown.setup.getNotes(), ColorScheme.LIGHT_GRAY_COLOR));
		}

		results.add(spacer(8));
		bankFilter.setAlignmentX(LEFT_ALIGNMENT);
		bankFilter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		results.add(bankFilter);

		results.add(spacer(10));
		results.add(smallLabel("Your items", VALUE));
		results.add(smallLabel(String.format("%,d", r.ownedCount) + " items owned", ColorScheme.LIGHT_GRAY_COLOR));
		results.add(wrappingText(r.bankStatus, ColorScheme.LIGHT_GRAY_COLOR));

		results.revalidate();
		results.repaint();
	}

	private static String groupName(String group)
	{
		return Character.toUpperCase(group.charAt(0)) + group.substring(1);
	}

	/** A row of tabs. Top row: melee/ranged/magic. Second row (melee): slash/crush/stab. */
	private JPanel tabs(GearReport r, String[] names, String selectedKey, boolean topRow)
	{
		JPanel row = new JPanel(new GridLayout(1, names.length));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, topRow ? 30 : 26));
		for (String name : names)
		{
			CombatStyle style = topRow ? styleFor(r, name) : CombatStyle.valueOf(name.toUpperCase(Locale.ROOT));
			String key = topRow ? name : style.name();
			SetupFinder.StyleResult res = r.results.get(style);
			boolean isSelected = key.equals(selectedKey);
			JLabel tab = new JLabel(topRow ? groupName(name) : name, SwingConstants.CENTER);
			tab.setFont(topRow ? FontManager.getRunescapeFont() : FontManager.getRunescapeSmallFont());
			tab.setForeground(res.immune ? ColorScheme.MEDIUM_GRAY_COLOR : isSelected ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
			tab.setBorder(BorderFactory.createMatteBorder(0, 0, isSelected ? 2 : 1, 0,
				isSelected ? VALUE : ColorScheme.DARK_GRAY_COLOR));
			String tip = res.immune ? "Immune" : res.dps == null ? null : String.format("%.2f DPS", res.dps.getDps());
			tab.setToolTipText(tip);
			if (!res.immune)
			{
				tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				tab.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseReleased(MouseEvent e)
					{
						shownStyle = style;
						userPickedTab = true;
						render();
					}
				});
			}
			row.add(tab);
		}
		return row;
	}

	/** For the top row: ranged/magic, or the best melee sub-style. */
	private CombatStyle styleFor(GearReport r, String group)
	{
		if (group.equals("ranged"))
		{
			return CombatStyle.RANGED;
		}
		if (group.equals("magic"))
		{
			return CombatStyle.MAGIC;
		}
		if (shownStyle != null && shownStyle.isMelee())
		{
			return shownStyle;
		}
		CombatStyle best = CombatStyle.SLASH;
		for (CombatStyle s : new CombatStyle[]{CombatStyle.STAB, CombatStyle.SLASH, CombatStyle.CRUSH})
		{
			if (r.results.get(s).dpsValue() > r.results.get(best).dpsValue())
			{
				best = s;
			}
		}
		return best;
	}

	private JPanel statsTable(DpsResult d)
	{
		JPanel table = new JPanel(new GridLayout(0, 2, 0, 1));
		table.setAlignmentX(LEFT_ALIGNMENT);
		addStat(table, "DPS", String.format("%.2f", d.getDps()));
		addStat(table, "Max hit", d.getMaxHit());
		addStat(table, "Accuracy", String.format("%.1f%%", d.getAccuracy() * 100));
		addStat(table, "Style", d.getStance());
		addStat(table, "Attack speed", d.getSpeedTicks() + " ticks (" + String.format("%.1fs", d.getSpeedTicks() * 0.6) + ")");
		addStat(table, "Prayer bonus", String.valueOf(d.getPrayerBonus()));
		table.setMaximumSize(new Dimension(Integer.MAX_VALUE, table.getPreferredSize().height));
		return table;
	}

	private static void addStat(JPanel table, String name, String value)
	{
		table.add(smallLabel(name, ColorScheme.LIGHT_GRAY_COLOR));
		JLabel v = smallLabel(value, VALUE);
		v.setHorizontalAlignment(SwingConstants.RIGHT);
		table.add(v);
	}

	/** The equipment screen: 3 columns x 5 rows of slots. */
	private JPanel equipmentGrid(GearResult gear)
	{
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setAlignmentX(LEFT_ALIGNMENT);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 2, 2, 2);
		for (Object[] cell : GRID)
		{
			EquipmentInventorySlot slot = (EquipmentInventorySlot) cell[0];
			c.gridy = (int) cell[1];
			c.gridx = (int) cell[2];
			grid.add(slotCell(slot, gear.get(slot), (int) cell[3]), c);
		}
		JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		wrap.setAlignmentX(LEFT_ALIGNMENT);
		wrap.add(grid);
		wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height + 4));
		return wrap;
	}

	private JLabel slotCell(EquipmentInventorySlot slot, Candidate item, int emptySprite)
	{
		JLabel cell = new JLabel();
		cell.setOpaque(true);
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cell.setHorizontalAlignment(SwingConstants.CENTER);
		Dimension size = new Dimension(SLOT_SIZE, SLOT_SIZE);
		cell.setPreferredSize(size);
		cell.setMinimumSize(size);
		if (item == null)
		{
			cell.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
			spriteManager.addSpriteTo(cell, emptySprite, 0);
			cell.setToolTipText(slotName(slot) + ": nothing suitable");
		}
		else
		{
			cell.setBorder(BorderFactory.createLineBorder(item.getSource().getColor()));
			itemManager.getImage(item.getItemId()).addTo(cell);
			cell.setToolTipText(slotName(slot) + ": " + item.getName() + " (" + item.getSource().getDisplayName() + ")");
		}
		return cell;
	}

	private JPanel spellSection(SpellAdvice spell)
	{
		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		box.setAlignmentX(LEFT_ALIGNMENT);

		box.add(smallLabel(spell.isCharges() ? "Recharging" : "Spell to autocast", ColorScheme.MEDIUM_GRAY_COLOR));
		box.add(wrappingText(spell.getTitle(), Color.WHITE));
		box.add(spacer(4));
		box.add(smallLabel(spell.isCharges() ? "Per charge:" : "Runes per cast:", ColorScheme.LIGHT_GRAY_COLOR));

		for (SpellAdvice.Line line : spell.getLines())
		{
			String text;
			Color color;
			if (line.isFree())
			{
				text = line.getPerCast() + " x " + line.getItem() + " - free (staff/tome)";
				color = ItemSource.EQUIPPED.getColor();
			}
			else
			{
				text = line.getPerCast() + " x " + line.getItem() + " - have " + String.format("%,d", line.getHave());
				color = line.getHave() >= line.getPerCast() ? Color.WHITE : WARNING;
			}
			box.add(wrappingText(text, color));
		}

		if (spell.getCasts() >= 0)
		{
			box.add(spacer(4));
			String what = spell.isCharges() ? "charges" : "casts";
			box.add(wrappingText("Enough for about " + String.format("%,d", spell.getCasts()) + " " + what + ".",
				spell.getCasts() > 0 ? ColorScheme.LIGHT_GRAY_COLOR : WARNING));
		}
		for (String note : spell.getNotes())
		{
			box.add(spacer(2));
			box.add(wrappingText(note, ColorScheme.MEDIUM_GRAY_COLOR));
		}
		return box;
	}

	// ---------------------------------------------------------------- small helpers

	private static JPanel labelled(String label, JComboBox<?> box)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setAlignmentX(LEFT_ALIGNMENT);
		JLabel l = smallLabel(label, ColorScheme.LIGHT_GRAY_COLOR);
		l.setPreferredSize(new Dimension(48, 24));
		row.add(l, BorderLayout.WEST);
		row.add(box, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		return row;
	}

	private static String slotName(EquipmentInventorySlot slot)
	{
		switch (slot)
		{
			case AMULET:
				return "Neck";
			case GLOVES:
				return "Hands";
			case BOOTS:
				return "Feet";
			default:
				String n = slot.name().toLowerCase(Locale.ROOT);
				return Character.toUpperCase(n.charAt(0)) + n.substring(1);
		}
	}

	private static JLabel smallLabel(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JTextArea wrappingText(String text, Color color)
	{
		JTextArea area = new JTextArea(text);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setEditable(false);
		area.setFocusable(false);
		area.setOpaque(false);
		area.setForeground(color);
		area.setFont(FontManager.getRunescapeSmallFont());
		area.setAlignmentX(LEFT_ALIGNMENT);
		area.setBorder(null);
		return area;
	}

	private static JPanel spacer(int height)
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setPreferredSize(new Dimension(0, height));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return p;
	}
}
