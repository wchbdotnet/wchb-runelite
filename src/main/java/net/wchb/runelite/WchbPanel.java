package net.wchb.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;
import net.wchb.runelite.model.WchbEvent;
import net.wchb.runelite.model.WchbFeed;
import net.wchb.runelite.model.WchbItem;
import net.wchb.runelite.model.WchbRegistration;

class WchbPanel extends PluginPanel
{
	private static final Color RED = new Color(218, 52, 43);
	private static final Color COPY = new Color(210, 213, 218);

	private final WchbPlugin plugin;
	private final ItemManager itemManager;
	private final JPanel contentPanel = new JPanel();
	private final JLabel status = new JLabel("Connection disabled");
	private final JLabel player = new JLabel("No WCHB profile connected");
	private final JLabel connectHint = paragraph("Enable Connect to WCHB in the plugin settings to continue.");
	private final JPanel onboardingPanel = new JPanel();
	private final JPanel dashboardPanel = new JPanel();
	private final JPanel dinkWarningPanel = new JPanel();
	private final JLabel dinkWarningText = paragraph("");
	private final JPanel setupPanel = new JPanel();
	private final JPanel guidePanel = new JPanel();
	private final JPanel changelogPanel = new JPanel();
	private final JPanel changelogContent = new JPanel();
	private final JPanel claimPanel = new JPanel();
	private final JPanel recentPanel = new JPanel();
	private final JPanel events = new JPanel();
	private final JTextArea webhookUrl = new JTextArea();
	private final JButton createProfile = new JButton("Create profile");
	private final JButton copyWebhook = new JButton("Copy Dink webhook");
	private final JButton claimProfile = new JButton("Claim profile on WCHB.net");
	private final JLabel claimStatus = new JLabel();
	private final JLabel connectionStep = new JLabel();
	private final JLabel profileStep = new JLabel();
	private final JLabel webhookStep = new JLabel();
	private final JLabel dinkStep = new JLabel();
	private final JLabel guideTitle = new JLabel();
	private final JPanel guideBody = new JPanel();
	private final JButton guideBack = new JButton("Back");
	private final JButton guideNext = new JButton("Next");
	private int guidePage;
	private boolean connectionEnabled;
	private boolean profileCreated;
	private boolean webhookCopied;
	private boolean dinkConnected;
	private boolean dinkHealthy = true;
	private boolean guideDismissed;
	private boolean guideOpenedFromDashboard;
	private boolean claimVisibleBeforeAlternateView;

	WchbPanel(WchbPlugin plugin, ItemManager itemManager)
	{
		super(false);
		this.plugin = plugin;
		this.itemManager = itemManager;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		buildOnboardingPanel();
		buildDashboardPanel();
		buildDinkWarningPanel();
		buildSetupPanel();
		buildGuidePanel();
		buildChangelogPanel();
		buildClaimPanel();
		buildRecentPanel();
		contentPanel.add(onboardingPanel);
		contentPanel.add(dashboardPanel);
		contentPanel.add(dinkWarningPanel);
		contentPanel.add(setupPanel);
		contentPanel.add(guidePanel);
		contentPanel.add(claimPanel);
		contentPanel.add(recentPanel);
		add(contentPanel, BorderLayout.NORTH);
		add(changelogPanel, BorderLayout.CENTER);

		showOnboarding();
	}

	private void buildOnboardingPanel()
	{
		onboardingPanel.setLayout(new BoxLayout(onboardingPanel, BoxLayout.Y_AXIS));
		onboardingPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		onboardingPanel.setAlignmentX(LEFT_ALIGNMENT);

		BufferedImage logo = ImageUtil.loadImageResource(WchbPlugin.class, "/wchb.png");
		Image scaled = logo.getScaledInstance(64, 64, Image.SCALE_SMOOTH);
		JLabel logoLabel = new JLabel(new ImageIcon(scaled));
		logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
		logoLabel.setAlignmentX(LEFT_ALIGNMENT);
		logoLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
		onboardingPanel.add(logoLabel);
		onboardingPanel.add(Box.createVerticalStrut(6));

		JLabel title = new JLabel("WHAT COULD HAVE BEEN?");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
		title.setHorizontalAlignment(SwingConstants.CENTER);
		title.setAlignmentX(LEFT_ALIGNMENT);
		title.setMaximumSize(new Dimension(Integer.MAX_VALUE, title.getPreferredSize().height));
		onboardingPanel.add(title);

		JLabel tagline = new JLabel("Your alternate OSRS loot journey");
		tagline.setForeground(RED);
		tagline.setFont(tagline.getFont().deriveFont(Font.BOLD, 11f));
		tagline.setHorizontalAlignment(SwingConstants.CENTER);
		tagline.setAlignmentX(LEFT_ALIGNMENT);
		tagline.setMaximumSize(new Dimension(Integer.MAX_VALUE, tagline.getPreferredSize().height));
		onboardingPanel.add(tagline);
		onboardingPanel.add(Box.createVerticalStrut(11));

		onboardingPanel.add(section("WHAT WE DO"));
		onboardingPanel.add(paragraph("Every real drop is rolled again against its drop table to show what you could have received instead."));
		onboardingPanel.add(Box.createVerticalStrut(6));
		onboardingPanel.add(callout("Your real loot never changes. WCHB creates a separate, completely fictional profile."));
		onboardingPanel.add(Box.createVerticalStrut(11));

		onboardingPanel.add(section("HOW IT WORKS"));
		onboardingPanel.add(step("1", "Dink sends your loot to your private WCHB webhook."));
		onboardingPanel.add(step("2", "WCHB rerolls it using the matching drop table."));
		onboardingPanel.add(step("3", "RuneLite shows the reveal and your recent reroll feed."));
		onboardingPanel.add(Box.createVerticalStrut(9));

		onboardingPanel.add(section("THE FULL EXPERIENCE"));
		onboardingPanel.add(paragraph("The plugin shows reveals and recent rerolls. WCHB.net adds your fictional bank, equipment manager, full history, profiles, leaderboards, and social features."));
		onboardingPanel.add(Box.createVerticalStrut(10));

		onboardingPanel.add(section("DINK + PRIVACY"));
		onboardingPanel.add(paragraph("Dink is the established Plugin Hub notifier that privately sends your loot to WCHB. We store the character, loot, and fictional-profile data needed for the experience—never Jagex credentials, chat, nearby players, or gameplay inputs."));
		onboardingPanel.add(Box.createVerticalStrut(11));

		onboardingPanel.add(section("START YOUR FICTIONAL PROFILE"));
		connectHint.setAlignmentX(LEFT_ALIGNMENT);
		onboardingPanel.add(connectHint);
		onboardingPanel.add(Box.createVerticalStrut(6));
		createProfile.setAlignmentX(LEFT_ALIGNMENT);
		createProfile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		createProfile.addActionListener(event -> plugin.createTemporaryProfile());
		onboardingPanel.add(createProfile);
		onboardingPanel.add(Box.createVerticalStrut(5));
		JButton connectExisting = new JButton("Connect existing account");
		connectExisting.setFont(connectExisting.getFont().deriveFont(12f));
		connectExisting.setAlignmentX(LEFT_ALIGNMENT);
		connectExisting.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		connectExisting.addActionListener(event -> plugin.openExistingAccountPage());
		onboardingPanel.add(connectExisting);
		onboardingPanel.add(Box.createVerticalStrut(6));
		JLabel noAccount = paragraph("No email or password is needed. After creation, we will give you the private webhook to paste into Dink.");
		noAccount.setForeground(new Color(170, 174, 181));
		onboardingPanel.add(noAccount);
	}

	private void buildDashboardPanel()
	{
		dashboardPanel.setLayout(new BoxLayout(dashboardPanel, BoxLayout.Y_AXIS));
		dashboardPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		dashboardPanel.setAlignmentX(LEFT_ALIGNMENT);

		JLabel title = new JLabel("WHAT COULD HAVE BEEN");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
		title.setAlignmentX(LEFT_ALIGNMENT);
		dashboardPanel.add(title);
		dashboardPanel.add(Box.createVerticalStrut(4));
		player.setForeground(Color.LIGHT_GRAY);
		player.setAlignmentX(LEFT_ALIGNMENT);
		dashboardPanel.add(player);
		dashboardPanel.add(Box.createVerticalStrut(10));

		JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		JButton changelog = new JButton("Changelog");
		changelog.addActionListener(event -> openChangelog());
		buttons.add(changelog);
		JButton open = new JButton("Open WCHB");
		open.addActionListener(event -> LinkBrowser.browse("https://wchb.net"));
		buttons.add(open);
		JButton guide = new JButton("Setup guide");
		guide.addActionListener(event ->
		{
			openGuide();
		});
		buttons.add(guide);
		dashboardPanel.add(buttons);
		dashboardPanel.add(Box.createVerticalStrut(14));
	}

	private void buildDinkWarningPanel()
	{
		Color warning = new Color(224, 164, 80);
		dinkWarningPanel.setLayout(new BoxLayout(dinkWarningPanel, BoxLayout.Y_AXIS));
		dinkWarningPanel.setBackground(new Color(35, 30, 24));
		dinkWarningPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, warning),
			BorderFactory.createEmptyBorder(10, 10, 10, 8)));
		dinkWarningPanel.setAlignmentX(LEFT_ALIGNMENT);

		JLabel title = new JLabel("DINK NEEDS ATTENTION");
		title.setForeground(warning);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
		title.setAlignmentX(LEFT_ALIGNMENT);
		dinkWarningPanel.add(title);
		dinkWarningPanel.add(Box.createVerticalStrut(6));

		dinkWarningText.setAlignmentX(LEFT_ALIGNMENT);
		dinkWarningPanel.add(dinkWarningText);
		dinkWarningPanel.add(Box.createVerticalStrut(8));

		JButton guide = new JButton("Open Dink setup guide");
		guide.setAlignmentX(LEFT_ALIGNMENT);
		guide.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		guide.addActionListener(event -> openGuide());
		dinkWarningPanel.add(guide);
		dinkWarningPanel.add(Box.createVerticalStrut(10));
		dinkWarningPanel.setVisible(false);
	}

	private void buildSetupPanel()
	{
		setupPanel.setLayout(new BoxLayout(setupPanel, BoxLayout.Y_AXIS));
		setupPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setupPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setupPanel.setAlignmentX(LEFT_ALIGNMENT);
		setupPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
		setupPanel.add(section("CONNECT DINK"));
		setupPanel.add(paragraph("Your temporary profile is ready. Copy the private webhook below into Dink. Your first loot event will connect this RuneScape character."));
		setupPanel.add(Box.createVerticalStrut(8));

		webhookUrl.setEditable(false);
		webhookUrl.setLineWrap(true);
		webhookUrl.setWrapStyleWord(false);
		webhookUrl.setRows(3);
		webhookUrl.setBackground(ColorScheme.DARK_GRAY_COLOR);
		webhookUrl.setForeground(Color.LIGHT_GRAY);
		webhookUrl.setFont(webhookUrl.getFont().deriveFont(11f));
		webhookUrl.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		webhookUrl.setAlignmentX(LEFT_ALIGNMENT);
		webhookUrl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
		setupPanel.add(webhookUrl);
		setupPanel.add(Box.createVerticalStrut(6));

		copyWebhook.setAlignmentX(LEFT_ALIGNMENT);
		copyWebhook.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		copyWebhook.addActionListener(event ->
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(webhookUrl.getText()), null);
			copyWebhook.setText("Webhook copied");
			webhookCopied = true;
			updateChecklist();
		});
		setupPanel.add(copyWebhook);
		setupPanel.add(Box.createVerticalStrut(5));

		JButton reopenGuide = new JButton("Open Dink setup guide");
		reopenGuide.setAlignmentX(LEFT_ALIGNMENT);
		reopenGuide.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		reopenGuide.addActionListener(event -> openGuide());
		setupPanel.add(reopenGuide);
		setupPanel.add(Box.createVerticalStrut(6));
	}

	private void buildGuidePanel()
	{
		guidePanel.setLayout(new BorderLayout(0, 8));
		guidePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		guidePanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(72, 74, 79)),
			BorderFactory.createEmptyBorder(12, 10, 12, 10)));
		guidePanel.setAlignmentX(LEFT_ALIGNMENT);
		guidePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 330));

		guideTitle.setForeground(RED);
		guideTitle.setFont(guideTitle.getFont().deriveFont(Font.BOLD, 13f));
		guidePanel.add(guideTitle, BorderLayout.NORTH);

		guideBody.setLayout(new BoxLayout(guideBody, BoxLayout.Y_AXIS));
		guideBody.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		configureChecklistLabel(connectionStep);
		configureChecklistLabel(profileStep);
		configureChecklistLabel(webhookStep);
		configureChecklistLabel(dinkStep);
		guidePanel.add(guideBody, BorderLayout.CENTER);

		JPanel navigation = new JPanel(new GridLayout(1, 3, 5, 0));
		navigation.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		guideBack.addActionListener(event -> showGuidePage(guidePage - 1));
		JButton close = new JButton("Close");
		close.addActionListener(event -> closeGuide());
		guideNext.addActionListener(event ->
		{
			if (guidePage == 4)
			{
				if (guideOpenedFromDashboard)
				{
					closeGuide();
				}
				else
				{
					guideDismissed = true;
					guidePanel.setVisible(false);
					showDashboard();
					setupPanel.setVisible(false);
					claimPanel.setVisible(true);
				}
				return;
			}
			showGuidePage(guidePage + 1);
		});
		navigation.add(guideBack);
		navigation.add(close);
		navigation.add(guideNext);
		guidePanel.add(navigation, BorderLayout.SOUTH);
		showGuidePage(0);
		updateChecklist();
	}

	private void buildChangelogPanel()
	{
		changelogPanel.setLayout(new BorderLayout());
		changelogPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		changelogPanel.setBorder(BorderFactory.createEmptyBorder(14, 10, 0, 10));

		changelogContent.setLayout(new BoxLayout(changelogContent, BoxLayout.Y_AXIS));
		changelogContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
		changelogContent.setBorder(BorderFactory.createEmptyBorder(0, 0, 30, 0));
		changelogContent.setAlignmentX(LEFT_ALIGNMENT);

		JLabel title = new JLabel("WCHB CHANGELOG");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
		title.setAlignmentX(LEFT_ALIGNMENT);
		changelogContent.add(title);
		changelogContent.add(Box.createVerticalStrut(4));
		changelogContent.add(paragraph("What has changed in the RuneLite companion."));
		changelogContent.add(Box.createVerticalStrut(12));

		// Add each release here, newest first, whenever the plugin is updated in a PR.
		addChangelogEntry("25 August 2026", new String[]
		{
			"Added four overlay styles: Default, Always Visible, Vs, and Classic. Default uses an animated drawer reveal that collapses back to the WCHB medallion.",
			"Added temporary reveal and opacity options, plus improved first-time overlay sizing and placement.",
			"Improved overlay clarity with a sharper logo, adaptive NPC-name sizing, cleaner multi-item layouts, and corrected dice animation.",
			"Added Dink setup health warnings and clearer help when loot delivery needs attention.",
			"Improved existing-account linking so completed browser connections are detected without restarting the plugin.",
			"Refined onboarding, sidebar spacing, recent-reroll cards, and item-icon loading.",
			"Added this dated in-plugin changelog."
		}, true);

		changelogContent.add(Box.createVerticalStrut(12));
		JButton back = new JButton("Back to rerolls");
		back.setAlignmentX(LEFT_ALIGNMENT);
		back.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		back.addActionListener(event -> closeChangelog());
		changelogContent.add(back);
		changelogContent.add(Box.createVerticalStrut(18));

		JScrollPane scrollPane = new JScrollPane(
			changelogContent,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(null);
		// Do not let the expanded release body become the preferred height of
		// RuneLite's sidebar (which can resize the game canvas/window).
		scrollPane.setPreferredSize(new Dimension(0, 0));
		scrollPane.setMinimumSize(new Dimension(0, 0));
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		changelogPanel.add(scrollPane, BorderLayout.CENTER);
		changelogPanel.setVisible(false);
	}

	private void addChangelogEntry(String date, String[] changes, boolean expanded)
	{
		JButton header = new JButton((expanded ? "\u25BE  " : "\u25B8  ") + date);
		header.setHorizontalAlignment(SwingConstants.LEFT);
		header.setAlignmentX(LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, RED),
			BorderFactory.createEmptyBorder(9, 9, 9, 5)));
		body.setAlignmentX(LEFT_ALIGNMENT);
		for (String change : changes)
		{
			JLabel bullet = paragraph("&#8226;&nbsp; " + change);
			bullet.setBorder(BorderFactory.createEmptyBorder(0, 0, 7, 0));
			body.add(bullet);
		}
		body.setVisible(expanded);

		header.addActionListener(event ->
		{
			boolean show = !body.isVisible();
			body.setVisible(show);
			header.setText((show ? "\u25BE  " : "\u25B8  ") + date);
			changelogContent.revalidate();
			changelogContent.repaint();
		});
		changelogContent.add(header);
		changelogContent.add(Box.createVerticalStrut(5));
		changelogContent.add(body);
		changelogContent.add(Box.createVerticalStrut(8));
	}

	private void buildClaimPanel()
	{
		claimPanel.setLayout(new BoxLayout(claimPanel, BoxLayout.Y_AXIS));
		claimPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		claimPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, RED),
			BorderFactory.createEmptyBorder(11, 11, 11, 10)));
		claimPanel.setAlignmentX(LEFT_ALIGNMENT);
		claimPanel.add(section("OPTIONAL FINAL STEP · KEEP YOUR PROFILE"));
		claimPanel.add(Box.createVerticalStrut(6));
		claimPanel.add(paragraph("Want the full WCHB experience? Claim this profile to keep your reroll history and unlock your fictional bank, equipment manager, full loot history, leaderboards, profiles, and social features."));
		claimPanel.add(Box.createVerticalStrut(8));
		claimProfile.setText("Claim my profile on WCHB.net");
		claimProfile.setAlignmentX(LEFT_ALIGNMENT);
		claimProfile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		claimProfile.addActionListener(event -> plugin.openClaimPage());
		claimPanel.add(claimProfile);
		claimPanel.add(Box.createVerticalStrut(6));
		claimStatus.setForeground(new Color(224, 164, 80));
		claimStatus.setFont(claimStatus.getFont().deriveFont(12f));
		claimStatus.setAlignmentX(LEFT_ALIGNMENT);
		claimStatus.setVisible(false);
		claimPanel.add(claimStatus);
		claimPanel.add(Box.createVerticalStrut(12));
	}

	private void openGuide()
	{
		guideDismissed = false;
		guideOpenedFromDashboard = dashboardPanel.isVisible() || recentPanel.isVisible();
		if (guideOpenedFromDashboard)
		{
			claimVisibleBeforeAlternateView = claimPanel.isVisible();
			dashboardPanel.setVisible(false);
			dinkWarningPanel.setVisible(false);
			setupPanel.setVisible(false);
			claimPanel.setVisible(false);
			recentPanel.setVisible(false);
			changelogPanel.setVisible(false);
		}
		dinkWarningPanel.setVisible(false);
		guidePanel.setVisible(true);
		showGuidePage(profileCreated ? 1 : 0);
	}

	private void closeGuide()
	{
		guideDismissed = true;
		guidePanel.setVisible(false);
		if (guideOpenedFromDashboard)
		{
			guideOpenedFromDashboard = false;
			showDashboard();
			claimPanel.setVisible(claimVisibleBeforeAlternateView);
		}
		updateDinkWarningVisibility();
	}

	private void openChangelog()
	{
		claimVisibleBeforeAlternateView = claimPanel.isVisible();
		contentPanel.setVisible(false);
		onboardingPanel.setVisible(false);
		dashboardPanel.setVisible(false);
		dinkWarningPanel.setVisible(false);
		setupPanel.setVisible(false);
		guidePanel.setVisible(false);
		claimPanel.setVisible(false);
		recentPanel.setVisible(false);
		changelogPanel.setVisible(true);
		changelogPanel.revalidate();
		changelogPanel.repaint();
	}

	private void closeChangelog()
	{
		changelogPanel.setVisible(false);
		contentPanel.setVisible(true);
		showDashboard();
		claimPanel.setVisible(claimVisibleBeforeAlternateView);
	}

	private void showGuidePage(int page)
	{
		guidePage = Math.max(0, Math.min(4, page));
		guideBody.removeAll();
		switch (guidePage)
		{
			case 0:
				guideTitle.setText("DINK SETUP · YOUR PROGRESS");
				guideBody.add(paragraph("Dink is a trusted Plugin Hub notifier. It detects your loot and privately sends it to WCHB for a fictional reroll."));
				guideBody.add(Box.createVerticalStrut(9));
				guideBody.add(connectionStep);
				guideBody.add(profileStep);
				guideBody.add(webhookStep);
				guideBody.add(dinkStep);
				break;
			case 1:
				guideTitle.setText("STEP 1 OF 4 · INSTALL DINK");
				guideBody.add(paragraph("1. Open RuneLite Configuration (wrench).<br><br>2. Open Plugin Hub (plug).<br><br>3. Search for <b>Dink</b> and click Install."));
				break;
			case 2:
				guideTitle.setText("STEP 2 OF 4 · ADD YOUR WEBHOOK");
				guideBody.add(paragraph("Open Dink settings and expand <b>Webhook Overrides</b>. Paste your private WCHB URL into <b>Loot Webhook Override</b>."));
				guideBody.add(Box.createVerticalStrut(9));
				guideBody.add(callout("Keep Discord in Primary Webhook URLs. Loot Webhook Override adds WCHB without replacing it."));
				break;
			case 3:
				guideTitle.setText("STEP 3 OF 4 · CONFIGURE LOOT");
				guideBody.add(paragraph("Minimise <b>Webhook Overrides</b>, expand the <b>Loot</b> tab, then match these settings in order:"));
				guideBody.add(Box.createVerticalStrut(7));
				guideBody.add(setting("Loot: Enabled", "ON"));
				guideBody.add(setting("Send Image", "OFF"));
				guideBody.add(setting("Min Loot Value", "1"));
				guideBody.add(setting("Include PK Loot", "OFF"));
				guideBody.add(setting("Include Clue Loot", "ON"));
				guideBody.add(Box.createVerticalStrut(7));
				guideBody.add(paragraph("Min Loot Value must be 1 so small drops are not filtered out."));
				break;
			default:
				guideTitle.setText("STEP 4 OF 4 · TEST IT");
				guideBody.add(paragraph("Kill a low-level NPC such as a chicken or goblin. Your reroll should appear here within a few seconds."));
				guideBody.add(Box.createVerticalStrut(9));
				JLabel help = paragraph("Nothing showing? Check the URL has no spaces and Min Loot Value is exactly 1.");
				help.setForeground(new Color(224, 164, 80));
				guideBody.add(help);
		}
		for (Component component : guideBody.getComponents())
		{
			if (component instanceof JComponent)
			{
				((JComponent) component).setAlignmentX(LEFT_ALIGNMENT);
			}
		}
		guideBack.setEnabled(guidePage > 0);
		guideNext.setText(guidePage == 4 ? "Done" : "Next");
		guideNext.setEnabled(guidePage < 4 || dinkConnected);
		guideBody.revalidate();
		guideBody.repaint();
	}

	private void buildRecentPanel()
	{
		recentPanel.setLayout(new BoxLayout(recentPanel, BoxLayout.Y_AXIS));
		recentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		recentPanel.setAlignmentX(LEFT_ALIGNMENT);
		recentPanel.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
		recentPanel.add(section("RECENT REROLLS"));
		recentPanel.add(Box.createVerticalStrut(6));
		events.setLayout(new BoxLayout(events, BoxLayout.Y_AXIS));
		events.setBackground(ColorScheme.DARK_GRAY_COLOR);
		events.setAlignmentX(LEFT_ALIGNMENT);
		recentPanel.add(events);
	}

	void setConnectionEnabled(boolean enabled)
	{
		connectionEnabled = enabled;
		createProfile.setEnabled(enabled);
		connectHint.setText(wrap(enabled
			? "Connection enabled. Create your temporary profile to continue."
			: "First enable Connect to WCHB in the plugin settings and accept RuneLite's network warning."));
		updateChecklist();
		updateDinkWarningVisibility();
	}

	void updateStatus(String text)
	{
		status.setText(text);
	}

	void updateClaimStatus(String text, boolean error)
	{
		claimStatus.setText(wrap(text));
		claimStatus.setForeground(error ? new Color(235, 96, 86) : new Color(224, 164, 80));
		claimStatus.setVisible(true);
		claimProfile.setEnabled(!"Creating secure claim link…".equals(text));
		claimPanel.revalidate();
	}

	void updateDinkHealth(boolean ready, String message)
	{
		dinkHealthy = ready;
		dinkWarningText.setText(wrap(message == null ? "" : message));
		updateDinkWarningVisibility();
	}

	void updateFeed(WchbFeed feed)
	{
		boolean alternateDashboardView = changelogPanel.isVisible()
			|| (guideOpenedFromDashboard && guidePanel.isVisible());
		boolean justConnected = !dinkConnected && feed.isDinkConnected();
		profileCreated = true;
		dinkConnected = feed.isDinkConnected();
		updateChecklist();
		setPlayerIdentity(feed.getPlayerName(), feed.getAccountType());
		status.setText(feed.isDinkConnected() ? "Dink connected through WCHB" : "Profile ready — connect Dink");
		if ("unclaimed".equals(feed.getProfileStatus()) && !feed.isDinkConnected())
		{
			showWebhook(feed.getWebhookUrl());
			claimPanel.setVisible(false);
			showSetupMode();
		}
		else
		{
			setupPanel.setVisible(false);
			boolean showClaim = "unclaimed".equals(feed.getProfileStatus());
			if (alternateDashboardView)
			{
				claimVisibleBeforeAlternateView = showClaim;
				claimPanel.setVisible(false);
			}
			else
			{
				showDashboard();
				claimPanel.setVisible(showClaim);
			}
			if (justConnected)
			{
				if (!guideOpenedFromDashboard)
				{
					guidePanel.setVisible(false);
				}
			}
		}
		populateEvents(feed.getEvents());
	}

	void updateRegistration(WchbRegistration registration)
	{
		profileCreated = true;
		dinkConnected = registration.isDinkConnected();
		updateChecklist();
		player.setText(registration.getPlayerName());
		status.setText(registration.isDinkConnected() ? "Dink connected through WCHB" : "Profile ready — connect Dink");
		if (registration.isDinkConnected())
		{
			showDashboard();
			setupPanel.setVisible(false);
			guidePanel.setVisible(false);
			claimPanel.setVisible(true);
		}
		else
		{
			showWebhook(registration.getWebhookUrl());
			claimPanel.setVisible(false);
			guideDismissed = false;
			showGuidePage(1);
			showSetupMode();
		}
		populateEvents(null);
	}

	private void showOnboarding()
	{
		contentPanel.setVisible(true);
		onboardingPanel.setVisible(true);
		dashboardPanel.setVisible(false);
		dinkWarningPanel.setVisible(false);
		setupPanel.setVisible(false);
		guidePanel.setVisible(false);
		changelogPanel.setVisible(false);
		claimPanel.setVisible(false);
		recentPanel.setVisible(false);
	}

	private void showDashboard()
	{
		contentPanel.setVisible(true);
		onboardingPanel.setVisible(false);
		dashboardPanel.setVisible(true);
		changelogPanel.setVisible(false);
		recentPanel.setVisible(true);
		updateDinkWarningVisibility();
	}

	private void showSetupMode()
	{
		contentPanel.setVisible(true);
		onboardingPanel.setVisible(false);
		dashboardPanel.setVisible(false);
		dinkWarningPanel.setVisible(false);
		setupPanel.setVisible(true);
		guidePanel.setVisible(!guideDismissed);
		changelogPanel.setVisible(false);
		claimPanel.setVisible(false);
		recentPanel.setVisible(false);
	}

	private void updateDinkWarningVisibility()
	{
		boolean show = connectionEnabled && profileCreated && dashboardPanel.isVisible()
			&& !guidePanel.isVisible() && !dinkHealthy;
		dinkWarningPanel.setVisible(show);
		dinkWarningPanel.revalidate();
		dinkWarningPanel.repaint();
	}

	private void showWebhook(String url)
	{
		setupPanel.setVisible(true);
		if (url != null && !url.isEmpty())
		{
			webhookUrl.setText(url);
			webhookUrl.setCaretPosition(0);
			copyWebhook.setText("Copy Dink webhook");
		}
	}

	private void setPlayerIdentity(String name, String accountType)
	{
		player.setText(name == null || name.isEmpty() ? "Connected" : name);
		String type = accountType == null ? "main" : accountType.toLowerCase().replace('-', '_').replace(' ', '_');
		if ("main".equals(type))
		{
			player.setIcon(null);
			return;
		}
		try
		{
			BufferedImage badge = ImageUtil.loadImageResource(WchbPlugin.class, "/" + type + ".png");
			player.setIcon(new ImageIcon(badge.getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
		}
		catch (RuntimeException ignored)
		{
			player.setIcon(null);
		}
	}

	private void populateEvents(List<WchbEvent> recent)
	{
		events.removeAll();
		if (recent == null || recent.isEmpty())
		{
			JLabel empty = new JLabel("Waiting for your first Dink event");
			empty.setForeground(Color.GRAY);
			events.add(empty);
		}
		else
		{
			recent.stream().limit(8).forEach(event -> events.add(createEventCard(event)));
		}
		events.revalidate();
		events.repaint();
	}

	private JPanel createEventCard(WchbEvent event)
	{
		JPanel card = new JPanel(new BorderLayout(6, 5));
		card.setBackground(new Color(14, 17, 21));
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 4, 0, 0, RED),
			BorderFactory.createEmptyBorder(8, 9, 8, 7)));
		card.setAlignmentX(LEFT_ALIGNMENT);
		JLabel source = new JLabel(event.getSource());
		source.setForeground(Color.WHITE);
		source.setFont(source.getFont().deriveFont(Font.BOLD, 14f));
		card.add(source, BorderLayout.NORTH);
		JPanel values = new JPanel();
		values.setLayout(new BoxLayout(values, BoxLayout.Y_AXIS));
		values.setBackground(new Color(14, 17, 21));
		values.add(createItemGroup("ACTUAL  " + WchbOverlay.formatGp(event.getTotalValue()), event.getItems(), COPY));
		values.add(Box.createVerticalStrut(4));
		values.add(createItemGroup("WCHB  " + WchbOverlay.formatGp(event.getRerolledValue()), event.getRerolledItems(),
			event.getRerolledValue() >= event.getTotalValue() ? new Color(103, 200, 122) : new Color(224, 164, 80)));
		card.add(values, BorderLayout.CENTER);
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		wrapper.add(card);
		return wrapper;
	}

	private JPanel createItemGroup(String heading, List<WchbItem> items, Color headingColor)
	{
		JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		group.setBackground(new Color(14, 17, 21));
		group.setAlignmentX(LEFT_ALIGNMENT);
		JLabel title = new JLabel(heading);
		title.setForeground(headingColor);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
		group.add(title);
		if (items != null)
		{
			for (WchbItem item : items)
			{
				if (item == null || item.getId() <= 0)
				{
					continue;
				}
				AsyncBufferedImage image = itemManager.getImage(item.getId(), Math.max(1, item.getQuantity()), false);
				JLabel icon = new JLabel();
				icon.setPreferredSize(new Dimension(28, 28));
				image.onLoaded(() -> SwingUtilities.invokeLater(() ->
				{
					Image scaled = image.getScaledInstance(26, 26, Image.SCALE_SMOOTH);
					icon.setIcon(new ImageIcon(scaled));
					icon.revalidate();
					icon.repaint();
				}));
				if (image.getWidth() > 0)
				{
					icon.setIcon(new ImageIcon(image.getScaledInstance(26, 26, Image.SCALE_SMOOTH)));
				}
				icon.setToolTipText(item.getName() + (item.getQuantity() > 1 ? " × " + item.getQuantity() : ""));
				group.add(icon);
			}
		}
		return group;
	}

	private static JLabel section(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(RED);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel paragraph(String text)
	{
		JLabel label = new JLabel(wrap(text));
		label.setForeground(COPY);
		label.setFont(label.getFont().deriveFont(14f));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static String wrap(String text)
	{
		// RuneLite can apply UI scaling after Swing has measured HTML labels.
		// Keep the logical width conservative so scaled glyphs wrap before the
		// sidebar edge instead of losing the final characters on each line.
		return wrap(text, 158);
	}

	private static String wrap(String text, int width)
	{
		return "<html><div style='width: " + width + "px'>" + text + "</div></html>";
	}

	private static JLabel callout(String text)
	{
		JLabel label = new JLabel(wrap("<b>" + text + "</b>", 136));
		label.setFont(label.getFont().deriveFont(14f));
		label.setAlignmentX(LEFT_ALIGNMENT);
		label.setForeground(Color.WHITE);
		label.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, RED),
			BorderFactory.createEmptyBorder(7, 9, 7, 4)));
		return label;
	}

	private static JLabel step(String number, String text)
	{
		JLabel label = paragraph("<b><font color='#DA342B'>" + number + ".</font></b> " + text);
		label.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
		return label;
	}

	private void updateChecklist()
	{
		setChecklist(connectionStep, connectionEnabled, "Connection enabled");
		setChecklist(profileStep, profileCreated, "Temporary profile created");
		setChecklist(webhookStep, webhookCopied || dinkConnected, "Private webhook copied into Dink");
		setChecklist(dinkStep, dinkConnected, "First Dink loot event received");
	}

	private static void configureChecklistLabel(JLabel label)
	{
		label.setFont(label.getFont().deriveFont(13f));
		label.setAlignmentX(LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
	}

	private static void setChecklist(JLabel label, boolean complete, String text)
	{
		label.setText((complete ? "✓  " : "○  ") + text);
		label.setForeground(complete ? new Color(103, 200, 122) : new Color(185, 188, 194));
	}

	private static JPanel setting(String name, String value)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
		JLabel key = new JLabel(name);
		key.setForeground(COPY);
		key.setFont(key.getFont().deriveFont(11.5f));
		JLabel val = new JLabel(value);
		val.setForeground(RED);
		val.setFont(val.getFont().deriveFont(Font.BOLD, 11.5f));
		row.add(key, BorderLayout.WEST);
		row.add(val, BorderLayout.EAST);
		return row;
	}
}
