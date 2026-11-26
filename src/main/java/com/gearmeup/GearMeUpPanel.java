package com.gearmeup;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Side panel: choose a boss (and setup), press the button, see one row per equipment slot.
 */
class GearMeUpPanel extends PluginPanel
{
	private static final Color WARNING = new Color(0xFF6B6B);

	private final GearMeUpPlugin plugin;
	private final ItemManager itemManager;

	private static final String ALL = "All bosses";

	private final List<Boss> bosses;
	private final IconTextField searchField = new IconTextField();
	private final JComboBox<String> categoryBox = new JComboBox<>();
	private final JComboBox<Boss> bossBox = new JComboBox<>();
	private final JComboBox<GearSetup> setupBox = new JComboBox<>();
	private final JCheckBox slayerBox = new JCheckBox("On slayer task");
	private final JPanel results = new JPanel();

	private static final String BANK_FILTER_OFF = "Show only this gear in bank";
	private static final String BANK_FILTER_ON = "Bank filtered (click to stop)";
	private final JToggleButton bankFilter = new JToggleButton(BANK_FILTER_OFF);

	GearMeUpPanel(GearMeUpPlugin plugin, ItemManager itemManager, List<Boss> bosses)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.bosses = bosses;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel controls = new JPanel(new GridLayout(0, 1, 0, 6));

		JLabel title = new JLabel("Gear Me Up");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setHorizontalAlignment(SwingConstants.CENTER);
		controls.add(title);

		controls.add(smallLabel("Search boss"));
		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchField.setToolTipText("Type a boss name or nickname (e.g. kq, cerb, tob). Enter gears you up.");
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refreshBosses();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refreshBosses();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refreshBosses();
			}
		});
		searchField.addClearListener(this::refreshBosses);
		searchField.addActionListener(e -> gearUp());
		controls.add(searchField);

		controls.add(smallLabel("Category"));
		Set<String> categories = new LinkedHashSet<>();
		categories.add(ALL);
		bosses.forEach(b -> categories.add(b.getCategory()));
		categories.forEach(categoryBox::addItem);
		categoryBox.addActionListener(e -> refreshBosses());
		controls.add(categoryBox);

		controls.add(smallLabel("Boss"));
		bossBox.addActionListener(e -> refreshSetups());
		controls.add(bossBox);

		controls.add(smallLabel("Setup"));
		controls.add(setupBox);

		slayerBox.setToolTipText("Prefer your slayer helmet / black mask (only works on a slayer task)");
		controls.add(slayerBox);

		JButton go = new JButton("Gear me up!");
		go.addActionListener(e -> gearUp());
		controls.add(go);

		bankFilter.setToolTipText("Filter the bank so it only shows the suggested items. Applies whenever the bank is open.");
		bankFilter.setEnabled(false);
		bankFilter.addActionListener(e ->
		{
			boolean on = bankFilter.isSelected();
			bankFilter.setText(on ? BANK_FILTER_ON : BANK_FILTER_OFF);
			plugin.setBankFilter(on);
		});
		controls.add(bankFilter);

		results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
		results.setBorder(new EmptyBorder(10, 0, 0, 0));
		refreshBosses();

		add(controls, BorderLayout.NORTH);
		add(results, BorderLayout.CENTER);
	}

	private void refreshBosses()
	{
		String query = searchField.getText() == null ? "" : searchField.getText().trim();
		// A search looks through every category; otherwise the category picker applies.
		String category = query.isEmpty() ? (String) categoryBox.getSelectedItem() : ALL;
		Boss previous = (Boss) bossBox.getSelectedItem();
		DefaultComboBoxModel<Boss> model = new DefaultComboBoxModel<>();
		bosses.stream()
			.filter(b -> ALL.equals(category) || b.getCategory().equals(category))
			.filter(b -> b.matches(query))
			.sorted((a, b) ->
			{
				// Best matches first when searching, then alphabetical.
				int byRank = query.isEmpty() ? 0 : Integer.compare(a.matchRank(query), b.matchRank(query));
				return byRank != 0 ? byRank : a.getName().compareToIgnoreCase(b.getName());
			})
			.forEach(model::addElement);
		bossBox.setModel(model);
		categoryBox.setEnabled(query.isEmpty());
		if (previous != null && model.getIndexOf(previous) >= 0 && query.isEmpty())
		{
			bossBox.setSelectedItem(previous);
		}
		if (model.getSize() == 0)
		{
			setupBox.setModel(new DefaultComboBoxModel<>());
			setupBox.setEnabled(false);
			results.removeAll();
			results.add(wrappingText("No boss matches \"" + query + "\". Try another name, or use \"Any monster (generic)\" "
				+ "and pick the style your target is weakest to.", ColorScheme.LIGHT_GRAY_COLOR));
			results.revalidate();
			results.repaint();
			return;
		}
		refreshSetups();
	}

	private void refreshSetups()
	{
		Boss boss = (Boss) bossBox.getSelectedItem();
		DefaultComboBoxModel<GearSetup> model = new DefaultComboBoxModel<>();
		if (boss != null)
		{
			for (GearSetup s : boss.getSetups())
			{
				model.addElement(s);
			}
		}
		setupBox.setModel(model);
		setupBox.setRenderer(new javax.swing.DefaultListCellRenderer()
		{
			@Override
			public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				Object text = value instanceof GearSetup ? ((GearSetup) value).getLabel() : value;
				return super.getListCellRendererComponent(list, text, index, selected, focus);
			}
		});
		setupBox.setEnabled(model.getSize() > 1);

		results.removeAll();
		if (boss != null && !boss.describe().isEmpty())
		{
			results.add(wrappingText(boss.describe(), ColorScheme.LIGHT_GRAY_COLOR));
			results.add(spacer(8));
		}
		results.add(wrappingText("Press \"Gear me up!\" to see your best setup. Open your bank once so your banked gear is included.",
			ColorScheme.MEDIUM_GRAY_COLOR));
		results.revalidate();
		results.repaint();
	}

	private void gearUp()
	{
		GearSetup setup = (GearSetup) setupBox.getSelectedItem();
		if (setup != null)
		{
			showMessage("Working it out...");
			plugin.gearUp(setup, slayerBox.isSelected());
		}
	}

	void showMessage(String message)
	{
		results.removeAll();
		results.add(wrappingText(message, ColorScheme.LIGHT_GRAY_COLOR));
		results.revalidate();
		results.repaint();
	}

	void showResult(GearResult result, SpellAdvice spell, String bankStatus)
	{
		results.removeAll();
		bankFilter.setEnabled(true);

		JLabel heading = new JLabel(result.getSetup().toString());
		heading.setFont(FontManager.getRunescapeBoldFont());
		heading.setForeground(ColorScheme.BRAND_ORANGE);
		heading.setAlignmentX(LEFT_ALIGNMENT);
		results.add(heading);
		Boss boss = result.getSetup().getBoss();
		if (boss != null && !boss.describe().isEmpty())
		{
			results.add(spacer(2));
			results.add(wrappingText(boss.describe(), ColorScheme.LIGHT_GRAY_COLOR));
		}
		results.add(spacer(6));

		for (EquipmentInventorySlot slot : GearCalculator.SLOTS)
		{
			results.add(slotRow(slot, result.get(slot)));
			results.add(spacer(3));
		}

		if (spell != null)
		{
			results.add(spacer(8));
			results.add(spellSection(spell));
		}

		for (String warning : result.getWarnings())
		{
			results.add(spacer(4));
			results.add(wrappingText("! " + warning, WARNING));
		}

		if (!result.getSetup().getNotes().isEmpty())
		{
			results.add(spacer(8));
			results.add(smallLabel("Tips"));
			results.add(wrappingText(result.getSetup().getNotes(), ColorScheme.LIGHT_GRAY_COLOR));
		}

		results.add(spacer(8));
		results.add(wrappingText(bankStatus, ColorScheme.MEDIUM_GRAY_COLOR));

		results.revalidate();
		results.repaint();
	}

	private JPanel spellSection(SpellAdvice spell)
	{
		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		box.setAlignmentX(LEFT_ALIGNMENT);

		JLabel header = new JLabel(spell.isCharges() ? "Recharging" : "Spell to autocast");
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		header.setAlignmentX(LEFT_ALIGNMENT);
		box.add(header);

		JTextArea title = wrappingText(spell.getTitle(), Color.WHITE);
		box.add(title);
		box.add(spacer(4));

		JLabel per = new JLabel(spell.isCharges() ? "Per charge:" : "Runes per cast:");
		per.setFont(FontManager.getRunescapeSmallFont());
		per.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		per.setAlignmentX(LEFT_ALIGNMENT);
		box.add(per);

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

	private JPanel slotRow(EquipmentInventorySlot slot, Candidate pick)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		if (pick != null)
		{
			itemManager.getImage(pick.getItemId()).addTo(icon);
		}
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel(new GridLayout(0, 1));
		text.setOpaque(false);

		JLabel slotName = new JLabel(slotDisplayName(slot));
		slotName.setFont(FontManager.getRunescapeSmallFont());
		slotName.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		text.add(slotName);

		JLabel itemName = new JLabel(pick == null ? "Nothing suitable" : pick.getName());
		itemName.setForeground(pick == null ? ColorScheme.MEDIUM_GRAY_COLOR : Color.WHITE);
		itemName.setFont(FontManager.getRunescapeSmallFont());
		text.add(itemName);

		if (pick != null)
		{
			itemName.setToolTipText(pick.getName() + " (" + pick.getSource().getDisplayName() + ")");
			JLabel source = new JLabel(pick.getSource().getDisplayName());
			source.setFont(FontManager.getRunescapeSmallFont());
			source.setForeground(pick.getSource().getColor());
			text.add(source);
		}

		row.add(text, BorderLayout.CENTER);
		return row;
	}

	private static String slotDisplayName(EquipmentInventorySlot slot)
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
				String n = slot.name().toLowerCase();
				return Character.toUpperCase(n.charAt(0)) + n.substring(1);
		}
	}

	private static JLabel smallLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
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
