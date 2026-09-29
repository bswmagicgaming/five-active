package com.bswmagicgaming.fiveactive;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FiveActivePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FiveActivePlugin.class);
		RuneLite.main(args);
	}
}
