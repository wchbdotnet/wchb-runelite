package net.wchb.runelite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

@Slf4j
class WchbConnectionStore
{
	private final ScheduledExecutorService executor;
	private final Path directory = RuneLite.RUNELITE_DIR.toPath().resolve("wchb-companion");
	private final Path file = directory.resolve("connections.properties");
	private final Object fileLock = new Object();

	@Inject
	WchbConnectionStore(ScheduledExecutorService executor)
	{
		this.executor = executor;
	}

	void find(String playerName, Consumer<String> callback)
	{
		executor.execute(() ->
		{
			String token = null;
			try
			{
				synchronized (fileLock)
				{
					Properties connections = load();
					token = connections.getProperty(normalise(playerName));
				}
			}
			catch (IOException error)
			{
				log.debug("Unable to read saved WCHB connections", error);
			}
			callback.accept(token);
		});
	}

	void remember(String playerName, String token)
	{
		if (playerName == null || playerName.trim().isEmpty() || token == null || token.length() < 8)
		{
			return;
		}
		executor.execute(() ->
		{
			try
			{
				synchronized (fileLock)
				{
					Properties connections = load();
					connections.setProperty(normalise(playerName), token);
					Files.createDirectories(directory);
					try (OutputStream output = Files.newOutputStream(file))
					{
						connections.store(output, "WCHB character connections");
					}
				}
			}
			catch (IOException error)
			{
				log.debug("Unable to save WCHB connection", error);
			}
		});
	}

	private Properties load() throws IOException
	{
		Properties connections = new Properties();
		if (Files.isRegularFile(file))
		{
			try (InputStream input = Files.newInputStream(file))
			{
				connections.load(input);
			}
		}
		return connections;
	}

	private static String normalise(String playerName)
	{
		return playerName.trim().replace('\u00a0', ' ').toLowerCase(Locale.ENGLISH);
	}
}
